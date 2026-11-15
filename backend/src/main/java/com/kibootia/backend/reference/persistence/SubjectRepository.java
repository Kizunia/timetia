package com.kibootia.backend.reference.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Subjects in MySQL; entity queries are tenant-filtered and skip soft-deleted rows (ADR 0028, 0034). */
interface SubjectRepository extends JpaRepository<SubjectEntity, UUID> {

	boolean existsByCode(String code);

	@Query("""
			select s from SubjectEntity s
			where (:pattern is null or lower(s.code) like :pattern escape '!' or lower(s.name) like :pattern escape '!')
			""")
	Page<SubjectEntity> search(String pattern, Pageable pageable);

}
