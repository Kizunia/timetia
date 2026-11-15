package com.kibootia.backend.reference.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Where the campus structure is kept. The use cases depend on this port, never on JPA (ADR 0003). Reads and writes act
 * on the current tenant's live rows; a code is unique per kind within a tenant, enforced by a unique key.
 */
public interface CampusStore {

	// --- departments ------------------------------------------------------------------------------------------------

	Department insertDepartment(String code, String name);

	Optional<Department> findDepartment(UUID id);

	Page<Department> searchDepartments(String text, Pageable pageable);

	/** Renames a department if it is still at {@code expectedVersion}; otherwise an optimistic locking failure. */
	Department renameDepartment(UUID id, long expectedVersion, String name);

	void deleteDepartment(UUID id);

	boolean departmentExists(UUID id);

	boolean departmentCodeInUse(String code);

	// --- divisions --------------------------------------------------------------------------------------------------

	Division insertDivision(String code, String name, UUID departmentId);

	Optional<Division> findDivision(UUID id);

	Page<Division> searchDivisions(UUID department, String text, Pageable pageable);

	Division renameDivision(UUID id, long expectedVersion, String name);

	void deleteDivision(UUID id);

	boolean divisionExists(UUID id);

	boolean divisionCodeInUse(String code);

	// --- batches ----------------------------------------------------------------------------------------------------

	Batch insertBatch(String code, String name, UUID divisionId);

	Optional<Batch> findBatch(UUID id);

	Page<Batch> searchBatches(UUID division, String text, Pageable pageable);

	Batch renameBatch(UUID id, long expectedVersion, String name);

	void deleteBatch(UUID id);

	boolean batchCodeInUse(String code);

}
