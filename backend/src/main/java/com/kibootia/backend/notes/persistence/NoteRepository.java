package com.kibootia.backend.notes.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface NoteRepository extends JpaRepository<NoteEntity, UUID> {

	/**
	 * Entity queries are filtered by tenant and skip deleted notes on their own. {@code pattern} is a lower-case LIKE
	 * pattern whose wildcards were escaped with {@code !}.
	 */
	@Query("""
			select n from NoteEntity n
			where (:owner is null or n.ownerId = :owner)
			  and (:pattern is null or lower(n.title) like :pattern escape '!' or lower(n.body) like :pattern escape '!')
			""")
	Page<NoteEntity> search(UUID owner, String pattern, Pageable pageable);

	@Query("""
			select count(n) > 0 from NoteEntity n
			where n.ownerId = :owner and n.title = :title and (:except is null or n.id <> :except)
			""")
	boolean titleInUse(UUID owner, String title, UUID except);

	/**
	 * Native SQL, because entity queries hide deleted rows. It therefore states the tenant itself (ADR 0028) and bumps
	 * the version so a concurrent edit is still detected.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			update note set deleted_at = null, version = version + 1, updated_at = :now, updated_by = :by
			where id = :id and tenant_id = :tenant and deleted_at is not null
			""", nativeQuery = true)
	int restore(byte[] id, byte[] tenant, LocalDateTime now, byte[] by);

	/** Native for the same reasons as {@link #restore}: it must reach deleted notes, so it states the tenant itself. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "delete from note where tenant_id = :tenant and owner_id = :owner", nativeQuery = true)
	int purgeOwnedBy(byte[] tenant, byte[] owner);

	/** Native for the same reasons as {@link #restore}. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "delete from note where tenant_id = :tenant and deleted_at is not null and deleted_at < :cutoff",
			nativeQuery = true)
	int purgeDeletedBefore(byte[] tenant, LocalDateTime cutoff);

}
