package com.kibootia.backend.academic.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.platform.authorization.Permission;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seeded roles against the role by permission table in the role and scope model document. The expected values are
 * written out here by name, not taken from the constants, so a change to a template or a permission name has to be made
 * on purpose in two places.
 */
class AcademicRolesTest {

	private static final Set<String> HEAD_OF_DEPARTMENT = Set.of("timetable.live.view", "rescheduling.tickets.create-on-behalf",
			"rescheduling.tickets.cancel-on-behalf", "rescheduling.tickets.review", "rescheduling.tickets.reject",
			"rescheduling.tickets.approve-permanent", "rescheduling.tickets.revert-in-scope",
			"rescheduling.tickets.edit-own", "rescheduling.tickets.view-in-scope",
			"rescheduling.ticket-audit.view-in-scope");

	private static final Set<String> REPRESENTATIVE = Set.of("timetable.live.view", "rescheduling.tickets.review",
			"rescheduling.tickets.reject", "rescheduling.tickets.view-in-scope", "rescheduling.ticket-audit.view-in-scope",
			"rescheduling.issues.open", "rescheduling.issues.close");

	private static final Map<String, String> SCOPE_TYPES = Map.of("Super HOD", "GLOBAL", "HOD", "DEPARTMENT", "Faculty",
			"GLOBAL", "CR", "DIVISION", "BR", "BATCH", "Student", "BATCH");

	private static final Map<String, Set<String>> PERMISSIONS = Map.of("Super HOD", HEAD_OF_DEPARTMENT, "HOD",
			HEAD_OF_DEPARTMENT, "Faculty",
			Set.of("timetable.live.view-own", "rescheduling.tickets.create", "rescheduling.tickets.cancel",
					"rescheduling.tickets.self-approve", "rescheduling.tickets.review", "rescheduling.tickets.reject",
					"rescheduling.tickets.revert", "rescheduling.tickets.edit-own", "rescheduling.tickets.view",
					"rescheduling.ticket-audit.view", "rescheduling.issues.close"),
			"CR", REPRESENTATIVE, "BR", REPRESENTATIVE, "Student", Set.of("timetable.live.view"));

	private final List<AcademicRoles.Template> templates = AcademicRoles.TEMPLATES;

	private final List<Permission> declared = new AcademicPermissions().permissions();

	@Test
	@DisplayName("there are exactly the six Timetia roles; Super Admin is the built-in Administrator and has no template")
	void sixRoles() {
		assertThat(this.templates).extracting(AcademicRoles.Template::name)
			.containsExactlyInAnyOrder("Super HOD", "HOD", "Faculty", "CR", "BR", "Student");
	}

	@Test
	@DisplayName("each role has the scope type of the model: global, department, division or batch")
	void scopeTypes() {
		for (AcademicRoles.Template template : this.templates) {
			assertThat(template.scopeType()).as(template.name()).isEqualTo(SCOPE_TYPES.get(template.name()));
		}
	}

	@Test
	@DisplayName("each role carries exactly the permissions the model gives it")
	void permissions() {
		for (AcademicRoles.Template template : this.templates) {
			assertThat(new TreeSet<>(template.permissions())).as(template.name())
				.containsExactlyElementsOf(new TreeSet<>(PERMISSIONS.get(template.name())));
		}
	}

	@Test
	@DisplayName("a template holds only declared, policy-decided permissions, as a role with a scope type must")
	void templatesFollowTheRoleRules() {
		Set<String> decided = this.declared.stream()
			.filter(Permission::policyDecided)
			.map(Permission::name)
			.collect(Collectors.toSet());
		for (AcademicRoles.Template template : this.templates) {
			assertThat(template.permissions()).as(template.name()).isSubsetOf(decided);
			assertThat(template.scopeType()).as(template.name()).isNotNull();
		}
	}

	@Test
	@DisplayName("every policy-decided permission is held by some role, and none of the Super Admin's three flat ones is")
	void everyPermissionHasAHolder() {
		Set<String> held = this.templates.stream().flatMap((template) -> template.permissions().stream()).collect(Collectors.toSet());
		Set<String> decided = this.declared.stream()
			.filter(Permission::policyDecided)
			.map(Permission::name)
			.collect(Collectors.toSet());
		Set<String> flat = this.declared.stream()
			.filter((permission) -> !permission.policyDecided())
			.map(Permission::name)
			.collect(Collectors.toSet());

		assertThat(held).containsExactlyInAnyOrderElementsOf(decided);
		assertThat(flat).containsExactlyInAnyOrder("timetable.live.view-all", "timetable.config.manage",
				"rescheduling.audit.view-all");
		assertThat(held).doesNotContainAnyElementsOf(flat);
	}

	@Test
	@DisplayName("nobody can override a conflict: no permission for it is declared")
	void noOverridePermission() {
		assertThat(this.declared).extracting(Permission::name).noneMatch((name) -> name.contains("override"));
	}

}
