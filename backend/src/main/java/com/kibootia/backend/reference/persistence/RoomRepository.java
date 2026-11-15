package com.kibootia.backend.reference.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Rooms in MySQL; entity queries are tenant-filtered and skip soft-deleted rows (ADR 0028, 0034). */
interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

	boolean existsByCode(String code);

	@Query("""
			select r from RoomEntity r
			where (:pattern is null or lower(r.code) like :pattern escape '!' or lower(r.name) like :pattern escape '!')
			""")
	Page<RoomEntity> search(String pattern, Pageable pageable);

}
