package com.kibootia.backend.identity.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.kibootia.backend.identity.application.UserProfile;
import com.kibootia.backend.identity.application.UserProfileStore;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Keeps profiles in MySQL. A live profile is a JPA entity with the conventions of every business table. What the entity
 * queries cannot see, deleted profiles, is read and changed with SQL that names the tenant itself (ADR 0028, ADR 0034):
 * restoring, marking an erasure, erasing and the lookups an administrator and the request gate need.
 */
@Repository
class JpaUserProfileStore implements UserProfileStore {

	private final EntityManager entityManager;

	private final JdbcClient jdbc;

	private final Clock clock;

	JpaUserProfileStore(EntityManager entityManager, JdbcClient jdbc, Clock clock) {
		this.entityManager = entityManager;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public Optional<UserProfile> find(UUID id) {
		return this.jdbc
			.sql("select " + UserProfileMapper.COLUMNS + " from user_profile where tenant_id = :tenant and id = :id")
			.param("tenant", tenant())
			.param("id", UuidBytes.toBytes(id))
			.query(UserProfileMapper::fromRow)
			.optional();
	}

	@Override
	public UserProfile insert(NewProfile profile) {
		UserProfileEntity entity = new UserProfileEntity(profile.id(), profile.status(), profile.facts(),
				profile.syncedAt());
		this.entityManager.persist(entity);
		// Flushed now so that a profile created at the same moment by another request is reported by this call.
		this.entityManager.flush();
		return UserProfileMapper.toModel(entity);
	}

	@Override
	public UserProfile refresh(UUID id, TokenFacts facts, Instant tokenIssuedAt) {
		UserProfileEntity entity = this.entityManager.find(UserProfileEntity.class, id);
		if (entity == null) {
			return find(id).orElseThrow();
		}
		boolean changed = false;
		if (entity.getStatus() == UserProfile.Status.INVITED) {
			entity.setStatus(UserProfile.Status.ACTIVE);
			changed = true;
		}
		if (tokenIssuedAt.isAfter(entity.getNameSyncedAt()) && entity.differsFrom(facts)) {
			entity.copy(facts, tokenIssuedAt);
			changed = true;
		}
		if (changed) {
			this.entityManager.flush();
		}
		return UserProfileMapper.toModel(entity);
	}

	@Override
	public void updateStatus(UUID id, UserProfile.Status status) {
		UserProfileEntity entity = this.entityManager.find(UserProfileEntity.class, id);
		entity.setStatus(status);
		this.entityManager.flush();
	}

	@Override
	public void updateTheme(UUID id, UserProfile.Theme theme) {
		UserProfileEntity entity = this.entityManager.find(UserProfileEntity.class, id);
		entity.setTheme(theme);
		this.entityManager.flush();
	}

	@Override
	public void softDelete(UUID id) {
		UserProfileEntity entity = this.entityManager.find(UserProfileEntity.class, id);
		this.entityManager.remove(entity);
		this.entityManager.flush();
	}

	@Override
	public boolean restore(UUID id, UUID actor) {
		this.entityManager.flush();
		this.entityManager.clear();
		return this.jdbc.sql("""
				update user_profile set deleted_at = null, version = version + 1, updated_at = :now, updated_by = :by
				where tenant_id = :tenant and id = :id and deleted_at is not null and erasure_requested_at is null
				""")
			.param("now", utc(this.clock.instant()))
			.param("by", UuidBytes.toBytes(actor))
			.param("tenant", tenant())
			.param("id", UuidBytes.toBytes(id))
			.update() == 1;
	}

	@Override
	public void markErasureRequested(UUID id, Instant at) {
		this.entityManager.flush();
		this.entityManager.clear();
		this.jdbc.sql("""
				update user_profile set erasure_requested_at = :at, version = version + 1, updated_at = :now
				where tenant_id = :tenant and id = :id and deleted_at is not null
				""")
			.param("at", utc(at))
			.param("now", utc(this.clock.instant()))
			.param("tenant", tenant())
			.param("id", UuidBytes.toBytes(id))
			.update();
	}

	@Override
	public void erase(UUID id) {
		this.entityManager.flush();
		this.entityManager.clear();
		this.jdbc.sql("delete from user_profile where tenant_id = :tenant and id = :id")
			.param("tenant", tenant())
			.param("id", UuidBytes.toBytes(id))
			.update();
	}

	@Override
	public Page<UserProfile> search(UserFilter filter, Pageable pageable) {
		StringBuilder where = new StringBuilder("tenant_id = :tenant");
		java.util.Map<String, Object> params = new java.util.HashMap<>();
		params.put("tenant", tenant());
		if (filter.state() == UserFilter.State.DELETED) {
			where.append(" and deleted_at is not null");
		}
		else {
			where.append(" and deleted_at is null");
			if (filter.state() != null) {
				where.append(" and status = :status");
				params.put("status", filter.state().name());
			}
		}
		if (filter.text() != null && !filter.text().isBlank()) {
			where.append(" and (lower(coalesce(email, '')) like :text escape '!'")
				.append(" or lower(coalesce(username, '')) like :text escape '!'")
				.append(" or lower(concat(coalesce(first_name, ''), ' ', coalesce(last_name, ''))) like :text escape '!')");
			params.put("text", "%" + filter.text().strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%")
				.replace("_", "!_") + "%");
		}
		long total = bind(this.jdbc.sql("select count(*) from user_profile where " + where), params)
			.query(Long.class)
			.single();
		JdbcClient.StatementSpec query = bind(this.jdbc.sql("select " + UserProfileMapper.COLUMNS
				+ " from user_profile where " + where + orderBy(pageable.getSort()) + " limit :limit offset :offset"),
				params);
		List<UserProfile> content = query.param("limit", pageable.getPageSize())
			.param("offset", pageable.getOffset())
			.query(UserProfileMapper::fromRow)
			.list();
		return new PageImpl<>(content, pageable, total);
	}

	@Override
	public List<UserProfile> findExpiredDeleted(Instant deletedBefore) {
		return this.jdbc.sql("select " + UserProfileMapper.COLUMNS + """
				 from user_profile
				where tenant_id = :tenant and deleted_at is not null and deleted_at < :cutoff
				  and erasure_requested_at is null
				""")
			.param("tenant", tenant())
			.param("cutoff", utc(deletedBefore))
			.query(UserProfileMapper::fromRow)
			.list();
	}

	@Override
	public boolean emailInUse(String email) {
		return this.jdbc.sql("select count(*) from user_profile where tenant_id = :tenant and lower(email) = :email")
			.param("tenant", tenant())
			.param("email", email.strip().toLowerCase(Locale.ROOT))
			.query(Long.class)
			.single() > 0;
	}

	// ---------------------------------------------------------------------------------------------------------------

	private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, java.util.Map<String, Object> params) {
		JdbcClient.StatementSpec bound = spec;
		for (java.util.Map.Entry<String, Object> param : params.entrySet()) {
			bound = bound.param(param.getKey(), param.getValue());
		}
		return bound;
	}

	/**
	 * The sort fields come from the controller's allow-list, and each is mapped to a column here by name, so nothing a
	 * caller sends reaches the SQL.
	 */
	private static String orderBy(Sort sort) {
		StringBuilder order = new StringBuilder(" order by ");
		boolean first = true;
		for (Sort.Order requested : sort) {
			String column = switch (requested.getProperty()) {
				case "email" -> "email";
				case "username" -> "username";
				case "name" -> "last_name, first_name";
				case "status" -> "status";
				case "createdAt" -> "created_at";
				default -> "id";
			};
			String direction = requested.isAscending() ? "asc" : "desc";
			for (String part : column.split(", ")) {
				order.append(first ? "" : ", ").append(part).append(' ').append(direction);
				first = false;
			}
		}
		return first ? " order by id asc" : order.toString();
	}

	/** DATETIME(6) columns hold UTC wall time, as Hibernate writes them. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static byte[] tenant() {
		return UuidBytes.toBytes(TenantContext.require());
	}

}
