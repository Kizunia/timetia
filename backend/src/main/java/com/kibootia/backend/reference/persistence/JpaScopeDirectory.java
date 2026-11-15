package com.kibootia.backend.reference.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.platform.authorization.ScopeDirectory;

/**
 * Answers which departments, divisions and batches exist, from the reference tables (Timetia ADR 0004, integration
 * contract). This is the provider the access module asks before a scoped role is given; with it present, a scoped role
 * can finally be assigned, and without the reference module nothing provides it and only global roles can be given.
 *
 * <p>Codes compare exactly, the same way every time: a code is matched as written, with no trimming or case change, so
 * the caller's rule "a code names at most one live place" is kept by the unique key on (tenant, code, active marker).
 * Every query is an entity query, so it is filtered to the current tenant and skips soft-deleted rows on its own (ADR
 * 0028, 0034); a deleted place is therefore invisible and its code is free to name a new one. A kind this provider does
 * not own (anything but the three academic kinds) is answered as no place, never as an error.
 */
@Component
@Transactional(readOnly = true)
class JpaScopeDirectory implements ScopeDirectory {

	private final DepartmentRepository departments;

	private final DivisionRepository divisions;

	private final BatchRepository batches;

	JpaScopeDirectory(DepartmentRepository departments, DivisionRepository divisions, BatchRepository batches) {
		this.departments = departments;
		this.divisions = divisions;
		this.batches = batches;
	}

	@Override
	public boolean exists(String kind, UUID id) {
		if (id == null) {
			return false;
		}
		// findById (not existsById) so the soft-delete filter is certain to apply: a deleted place must not exist.
		return switch (kind) {
			case AcademicScopes.DEPARTMENT -> this.departments.findById(id).isPresent();
			case AcademicScopes.DIVISION -> this.divisions.findById(id).isPresent();
			case AcademicScopes.BATCH -> this.batches.findById(id).isPresent();
			default -> false;
		};
	}

	@Override
	public Optional<UUID> idOf(String kind, String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		return switch (kind) {
			case AcademicScopes.DEPARTMENT -> this.departments.findIdByCode(code);
			case AcademicScopes.DIVISION -> this.divisions.findIdByCode(code);
			case AcademicScopes.BATCH -> this.batches.findIdByCode(code);
			default -> Optional.empty();
		};
	}

	@Override
	public Map<UUID, String> codesOf(String kind, Collection<UUID> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		Map<UUID, String> codes = new HashMap<>();
		switch (kind) {
			case AcademicScopes.DEPARTMENT ->
				this.departments.findByIdIn(ids).forEach((d) -> codes.put(d.getId(), d.getCode()));
			case AcademicScopes.DIVISION ->
				this.divisions.findByIdIn(ids).forEach((d) -> codes.put(d.getId(), d.getCode()));
			case AcademicScopes.BATCH -> this.batches.findByIdIn(ids).forEach((b) -> codes.put(b.getId(), b.getCode()));
			default -> {
				// A kind this provider does not own has no codes here.
			}
		}
		return codes;
	}

}
