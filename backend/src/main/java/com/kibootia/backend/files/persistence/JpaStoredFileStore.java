package com.kibootia.backend.files.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.files.application.ConditionalOnFilesEnabled;
import com.kibootia.backend.files.application.StoredFileStore;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.persistence.UuidV7;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Keeps file metadata in MySQL. A new row is a JPA entity with the conventions of every business table. What entity
 * queries cannot see, deleted files, and what must reach every row, erasing an owner's files, is read and changed with
 * SQL that names the tenant itself (ADR 0028, ADR 0034).
 */
@Repository
@ConditionalOnFilesEnabled
class JpaStoredFileStore implements StoredFileStore {

	private static final String COLUMNS = "id, tenant_id, owner_id, category, content_type, renditions";

	private final EntityManager entityManager;

	private final JdbcClient jdbc;

	private final Clock clock;

	JpaStoredFileStore(EntityManager entityManager, JdbcClient jdbc, Clock clock) {
		this.entityManager = entityManager;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public UUID nextId() {
		return UuidV7.next(this.clock);
	}

	/** Its own short transaction: the storage server is never called while one is open. */
	@Override
	@Transactional
	public void insert(StoredFile file) {
		this.entityManager.persist(new StoredFileEntity(file.id(), file.ownerId(), file.category(), file.contentType(),
				String.join(",", file.renditions())));
		this.entityManager.flush();
	}

	@Override
	public Optional<StoredFile> find(UUID id) {
		return this.jdbc
			.sql("select " + COLUMNS + " from stored_file where tenant_id = :tenant and id = :id and deleted_at is null")
			.param("tenant", tenant())
			.param("id", UuidBytes.toBytes(id))
			.query(JpaStoredFileStore::fromRow)
			.optional();
	}

	@Override
	public boolean softDelete(UUID id) {
		return this.jdbc.sql("""
				update stored_file set deleted_at = :now, version = version + 1, updated_at = :now
				where tenant_id = :tenant and id = :id and deleted_at is null
				""")
			.param("now", utc(this.clock.instant()))
			.param("tenant", tenant())
			.param("id", UuidBytes.toBytes(id))
			.update() == 1;
	}

	@Override
	public List<StoredFile> findDeletedBefore(Instant cutoff, int limit) {
		return this.jdbc.sql("select " + COLUMNS + """
				 from stored_file
				where tenant_id = :tenant and deleted_at is not null and deleted_at < :cutoff
				order by deleted_at limit :limit
				""")
			.param("tenant", tenant())
			.param("cutoff", utc(cutoff))
			.param("limit", limit)
			.query(JpaStoredFileStore::fromRow)
			.list();
	}

	@Override
	public List<StoredFile> findByOwner(UUID owner) {
		return this.jdbc.sql("select " + COLUMNS + " from stored_file where tenant_id = :tenant and owner_id = :owner")
			.param("tenant", tenant())
			.param("owner", UuidBytes.toBytes(owner))
			.query(JpaStoredFileStore::fromRow)
			.list();
	}

	@Override
	public void deleteAll(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return;
		}
		this.jdbc.sql("delete from stored_file where tenant_id = :tenant and id in (:ids)")
			.param("tenant", tenant())
			.param("ids", ids.stream().map(UuidBytes::toBytes).toList())
			.update();
	}

	@Override
	public Set<UUID> existing(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		List<byte[]> found = this.jdbc.sql("select id from stored_file where tenant_id = :tenant and id in (:ids)")
			.param("tenant", tenant())
			.param("ids", ids.stream().map(UuidBytes::toBytes).toList())
			.query((rs, row) -> rs.getBytes("id"))
			.list();
		Set<UUID> existing = new HashSet<>();
		found.forEach((bytes) -> existing.add(UuidBytes.fromBytes(bytes)));
		return existing;
	}

	@Override
	public List<UUID> idsCreatedBefore(Instant before, UUID after, int limit) {
		// BINARY(16) compares byte by byte, and a UUIDv7 starts with its time, so key order is creation order.
		byte[] start = (after != null) ? UuidBytes.toBytes(after) : new byte[16];
		return this.jdbc.sql("""
				select id from stored_file
				where tenant_id = :tenant and created_at < :before and id > :after
				order by id limit :limit
				""")
			.param("tenant", tenant())
			.param("before", utc(before))
			.param("after", start)
			.param("limit", limit)
			.query((rs, row) -> UuidBytes.fromBytes(rs.getBytes("id")))
			.list();
	}

	private static StoredFile fromRow(ResultSet rs, int row) throws SQLException {
		return new StoredFile(UuidBytes.fromBytes(rs.getBytes("id")), UuidBytes.fromBytes(rs.getBytes("tenant_id")),
				UuidBytes.fromBytes(rs.getBytes("owner_id")), rs.getString("category"), rs.getString("content_type"),
				Arrays.asList(rs.getString("renditions").split(",")));
	}

	/** DATETIME(6) columns hold UTC wall time, as Hibernate writes them. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static byte[] tenant() {
		return UuidBytes.toBytes(TenantContext.require());
	}

}
