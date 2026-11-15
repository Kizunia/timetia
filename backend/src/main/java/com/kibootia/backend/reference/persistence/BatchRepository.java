package com.kibootia.backend.reference.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Batches in MySQL; like {@link DepartmentRepository}, it answers only about live rows of the current tenant. */
interface BatchRepository extends JpaRepository<BatchEntity, UUID> {

	@Query("select b.id from BatchEntity b where b.code = :code")
	Optional<UUID> findIdByCode(String code);

	List<BatchEntity> findByIdIn(Collection<UUID> ids);

	boolean existsByCode(String code);

	List<BatchEntity> findByDivisionIdIn(Collection<UUID> divisionIds);

	@Query("""
			select b from BatchEntity b
			where (:division is null or b.divisionId = :division)
			  and (:pattern is null or lower(b.code) like :pattern escape '!' or lower(b.name) like :pattern escape '!')
			""")
	Page<BatchEntity> search(UUID division, String pattern, Pageable pageable);

}
