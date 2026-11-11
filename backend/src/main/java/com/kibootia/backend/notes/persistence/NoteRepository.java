package com.kibootia.backend.notes.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

	/**
	 * A live note, locked until the end of the transaction, so that two changes of its attachment happen one after the
	 * other and each sees which file the other left.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from NoteEntity n where n.id = :id")
	Optional<NoteEntity> lockById(UUID id);

	/**
	 * Sets or clears the attachment of a live note. A statement of its own, because the entity never writes the column,
	 * and native, so it states the tenant (ADR 0028). The version is left alone: attaching a picture is not an edit of the
	 * text, and must not make an editor that is open fail its next save.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "update note set attachment_file_id = :file where id = :id and tenant_id = :tenant and deleted_at is null",
			nativeQuery = true)
	int setAttachment(byte[] id, byte[] tenant, byte[] file);

	/** The notes the retention purge is about to remove that have a file: note ID and file ID, as raw bytes. */
	@Query(value = """
			select id, attachment_file_id from note
			where tenant_id = :tenant and deleted_at is not null and deleted_at < :cutoff and attachment_file_id is not null
			""", nativeQuery = true)
	List<Object[]> attachmentsDeletedBefore(byte[] tenant, LocalDateTime cutoff);

}
