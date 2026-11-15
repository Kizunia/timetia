package com.kibootia.backend.reference.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Academic terms in MySQL; entity queries are tenant-filtered and skip soft-deleted rows (ADR 0028, 0034). */
interface AcademicTermRepository extends JpaRepository<AcademicTermEntity, UUID> {

	boolean existsByCode(String code);

	@Query("""
			select t from AcademicTermEntity t
			where (:pattern is null or lower(t.code) like :pattern escape '!' or lower(t.name) like :pattern escape '!')
			""")
	Page<AcademicTermEntity> search(String pattern, Pageable pageable);

}
