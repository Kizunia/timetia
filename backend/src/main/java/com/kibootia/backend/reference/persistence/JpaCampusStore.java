package com.kibootia.backend.reference.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.kibootia.backend.reference.application.Batch;
import com.kibootia.backend.reference.application.CampusStore;
import com.kibootia.backend.reference.application.Department;
import com.kibootia.backend.reference.application.Division;

/** Keeps the campus structure in MySQL through JPA. A new row is flushed at once so a duplicate code is reported now. */
@Component
class JpaCampusStore implements CampusStore {

	private final DepartmentRepository departments;

	private final DivisionRepository divisions;

	private final BatchRepository batches;

	JpaCampusStore(DepartmentRepository departments, DivisionRepository divisions, BatchRepository batches) {
		this.departments = departments;
		this.divisions = divisions;
		this.batches = batches;
	}

	// --- departments ------------------------------------------------------------------------------------------------

	@Override
	public Department insertDepartment(String code, String name) {
		return CampusEntityMapper.toModel(this.departments.saveAndFlush(new DepartmentEntity(code, name)));
	}

	@Override
	public Optional<Department> findDepartment(UUID id) {
		return this.departments.findById(id).map(CampusEntityMapper::toModel);
	}

	@Override
	public Page<Department> searchDepartments(String text, Pageable pageable) {
		return this.departments.search(likePattern(text), pageable).map(CampusEntityMapper::toModel);
	}

	@Override
	public Department renameDepartment(UUID id, long expectedVersion, String name) {
		DepartmentEntity entity = this.departments.findById(id).orElseThrow();
		requireVersion(entity.getVersion(), expectedVersion, DepartmentEntity.class, id);
		entity.rename(name);
		return CampusEntityMapper.toModel(this.departments.saveAndFlush(entity));
	}

	@Override
	public void deleteDepartment(UUID id) {
		this.departments.findById(id).ifPresent(this.departments::delete);
		this.departments.flush();
	}

	@Override
	public boolean departmentExists(UUID id) {
		return this.departments.findById(id).isPresent();
	}

	@Override
	public boolean departmentCodeInUse(String code) {
		return this.departments.existsByCode(code);
	}

	// --- divisions --------------------------------------------------------------------------------------------------

	@Override
	public Division insertDivision(String code, String name, UUID departmentId) {
		return CampusEntityMapper.toModel(this.divisions.saveAndFlush(new DivisionEntity(code, name, departmentId)));
	}

	@Override
	public Optional<Division> findDivision(UUID id) {
		return this.divisions.findById(id).map(CampusEntityMapper::toModel);
	}

	@Override
	public Page<Division> searchDivisions(UUID department, String text, Pageable pageable) {
		return this.divisions.search(department, likePattern(text), pageable).map(CampusEntityMapper::toModel);
	}

	@Override
	public Division renameDivision(UUID id, long expectedVersion, String name) {
		DivisionEntity entity = this.divisions.findById(id).orElseThrow();
		requireVersion(entity.getVersion(), expectedVersion, DivisionEntity.class, id);
		entity.rename(name);
		return CampusEntityMapper.toModel(this.divisions.saveAndFlush(entity));
	}

	@Override
	public void deleteDivision(UUID id) {
		this.divisions.findById(id).ifPresent(this.divisions::delete);
		this.divisions.flush();
	}

	@Override
	public boolean divisionExists(UUID id) {
		return this.divisions.findById(id).isPresent();
	}

	@Override
	public boolean divisionCodeInUse(String code) {
		return this.divisions.existsByCode(code);
	}

	// --- batches ----------------------------------------------------------------------------------------------------

	@Override
	public Batch insertBatch(String code, String name, UUID divisionId) {
		return CampusEntityMapper.toModel(this.batches.saveAndFlush(new BatchEntity(code, name, divisionId)));
	}

	@Override
	public Optional<Batch> findBatch(UUID id) {
		return this.batches.findById(id).map(CampusEntityMapper::toModel);
	}

	@Override
	public Page<Batch> searchBatches(UUID division, String text, Pageable pageable) {
		return this.batches.search(division, likePattern(text), pageable).map(CampusEntityMapper::toModel);
	}

	@Override
	public Batch renameBatch(UUID id, long expectedVersion, String name) {
		BatchEntity entity = this.batches.findById(id).orElseThrow();
		requireVersion(entity.getVersion(), expectedVersion, BatchEntity.class, id);
		entity.rename(name);
		return CampusEntityMapper.toModel(this.batches.saveAndFlush(entity));
	}

	@Override
	public void deleteBatch(UUID id) {
		this.batches.findById(id).ifPresent(this.batches::delete);
		this.batches.flush();
	}

	@Override
	public boolean batchCodeInUse(String code) {
		return this.batches.existsByCode(code);
	}

	// --- bulk lookups -----------------------------------------------------------------------------------------------

	@Override
	public List<Division> findDivisions(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of()
				: this.divisions.findAllById(ids).stream().map(CampusEntityMapper::toModel).toList();
	}

	@Override
	public List<Batch> findBatches(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : this.batches.findAllById(ids).stream().map(CampusEntityMapper::toModel).toList();
	}

	@Override
	public List<Batch> findBatchesOfDivisions(Collection<UUID> divisionIds) {
		return divisionIds.isEmpty() ? List.of()
				: this.batches.findByDivisionIdIn(divisionIds).stream().map(CampusEntityMapper::toModel).toList();
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	/** Fails the write when the caller's version is stale, surfaced as 409 (ADR 0043), matching the notes store. */
	private static void requireVersion(long actual, long expected, Class<?> type, UUID id) {
		if (actual != expected) {
			throw new ObjectOptimisticLockingFailureException(type, id);
		}
	}

	/** A lower-case LIKE pattern matching the text anywhere, with the user's own % and _ made literal; null for all. */
	private static String likePattern(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String escaped = text.strip().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escaped + "%";
	}

}
