package com.kibootia.backend.reference.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Faculty in MySQL; entity queries are tenant-filtered and skip soft-deleted rows (ADR 0028, 0034). */
interface FacultyRepository extends JpaRepository<FacultyEntity, UUID> {

	boolean existsByCode(String code);

	@Query("select f.id from FacultyEntity f where f.userId = :userId")
	List<UUID> findIdsByUserId(UUID userId);

	@Query("""
			select f from FacultyEntity f
			where (:department is null or f.departmentId = :department)
			  and (:pattern is null or lower(f.code) like :pattern escape '!' or lower(f.name) like :pattern escape '!')
			""")
	Page<FacultyEntity> search(UUID department, String pattern, Pageable pageable);

}
