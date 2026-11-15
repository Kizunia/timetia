package com.kibootia.backend.reference.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Departments in MySQL. Entity queries are filtered by tenant and skip soft-deleted rows on their own (ADR 0028, 0034),
 * so every method here answers only about live departments of the current tenant.
 */
interface DepartmentRepository extends JpaRepository<DepartmentEntity, UUID> {

	@Query("select d.id from DepartmentEntity d where d.code = :code")
	Optional<UUID> findIdByCode(String code);

	List<DepartmentEntity> findByIdIn(Collection<UUID> ids);

	boolean existsByCode(String code);

	/** {@code pattern} is a lower-case LIKE pattern whose wildcards were escaped with {@code !}, or null for all. */
	@Query("""
			select d from DepartmentEntity d
			where (:pattern is null or lower(d.code) like :pattern escape '!' or lower(d.name) like :pattern escape '!')
			""")
	Page<DepartmentEntity> search(String pattern, Pageable pageable);

}
