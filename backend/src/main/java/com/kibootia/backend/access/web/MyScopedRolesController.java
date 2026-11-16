package com.kibootia.backend.access.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.access.application.ScopedAssignmentService;

/**
 * The roles the caller holds within places (a department, a division, a batch, or everywhere). A screen uses it to
 * preselect the caller's own places; it grants nothing, because the server decides what the caller may do.
 */
@RestController
@RequestMapping("/api/v1/me/scoped-roles")
class MyScopedRolesController {

	private final ScopedAssignmentService assignments;

	MyScopedRolesController(ScopedAssignmentService assignments) {
		this.assignments = assignments;
	}

	@GetMapping
	List<ScopedRoleResponse> get() {
		return ScopedUserRolesController.toResponses(this.assignments.myScopedRoles());
	}

}
