package com.kibootia.backend.reference.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Divisions in MySQL; like {@link DepartmentRepository}, it answers only about live rows of the current tenant. */
interface DivisionRepository extends JpaRepository<DivisionEntity, UUID> {

	@Query("select d.id from DivisionEntity d where d.code = :code")
	Optional<UUID> findIdByCode(String code);

	List<DivisionEntity> findByIdIn(Collection<UUID> ids);

	boolean existsByCode(String code);

	@Query("""
			select d from DivisionEntity d
			where (:department is null or d.departmentId = :department)
			  and (:pattern is null or lower(d.code) like :pattern escape '!' or lower(d.name) like :pattern escape '!')
			""")
	Page<DivisionEntity> search(UUID department, String pattern, Pageable pageable);

}
