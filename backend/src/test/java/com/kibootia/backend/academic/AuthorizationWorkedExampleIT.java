package com.kibootia.backend.academic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.persistence.UuidBytes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The worked example (W4.1): how a module that owns tickets uses authorization, written as it would be in that module
 * and run against the real decision point, the real roles and a real database. {@link ExampleTickets} is the model.
 *
 * <p>The module's own parts are the table, the facts it builds from a row, and the state rules. Security's parts are
 * the two calls: {@code authorization.can}/{@code require} for one record, and {@code authorization.reach} for a list.
 * The list is the only place a module can get this wrong silently, so the test proves it agrees with the single-record
 * decision for every person and every ticket.
 */
class AuthorizationWorkedExampleIT extends AcademicApiTest {

	static final UUID T_THEORY_A = UUID.fromString("77000000-0000-7000-8000-000000000001");

	static final UUID T_LAB_A = UUID.fromString("77000000-0000-7000-8000-000000000002");

	static final UUID T_THEORY_B = UUID.fromString("77000000-0000-7000-8000-000000000003");

	static final UUID T_LAB_B = UUID.fromString("77000000-0000-7000-8000-000000000004");

	static final UUID T_TUTORIAL_A = UUID.fromString("77000000-0000-7000-8000-000000000005");

	ExampleTickets tickets;

	UUID faculty1;

	UUID faculty2;

	@BeforeEach
	void createTable() throws Exception {
		this.jdbc.execute("drop table if exists example_ticket_batch");
		this.jdbc.execute("drop table if exists example_ticket");
		this.jdbc.execute("""
				create table example_ticket (
				  id binary(16) primary key, tenant_id binary(16) not null, department_id binary(16) not null,
				  division_id binary(16) not null, session_type varchar(10) not null, author_user_id binary(16) not null,
				  permanent_change boolean not null default false)
				""");
		this.jdbc.execute("create table example_ticket_batch (ticket_id binary(16) not null, batch_id binary(16) not null)");
		this.tickets = new ExampleTickets(this.authorization, this.jdbc);

		this.faculty1 = faculty();
		this.faculty2 = faculty();
		insert(T_THEORY_A, Facts.DEPARTMENT_A, Facts.DIVISION_1, Facts.BATCH_1, SessionType.THEORY, this.faculty1);
		insert(T_LAB_A, Facts.DEPARTMENT_A, Facts.DIVISION_1, Facts.BATCH_1, SessionType.LAB, this.faculty1);
		insert(T_THEORY_B, Facts.DEPARTMENT_B, Facts.DIVISION_2, Facts.BATCH_2, SessionType.THEORY, this.faculty2);
		insert(T_LAB_B, Facts.DEPARTMENT_B, Facts.DIVISION_2, Facts.BATCH_2, SessionType.LAB, this.faculty2);
		insert(T_TUTORIAL_A, Facts.DEPARTMENT_A, Facts.DIVISION_1, Facts.BATCH_1, SessionType.TUTORIAL, this.faculty2);
	}

	@AfterEach
	void dropTable() {
		this.jdbc.execute("drop table if exists example_ticket_batch");
		this.jdbc.execute("drop table if exists example_ticket");
	}

	private void insert(UUID id, UUID department, UUID division, UUID batch, SessionType type, UUID author) {
		this.jdbc.update("insert into example_ticket (id, tenant_id, department_id, division_id, session_type, author_user_id) "
				+ "values (?, ?, ?, ?, ?, ?)", UuidBytes.toBytes(id), UuidBytes.toBytes(Facts.TENANT),
				UuidBytes.toBytes(department), UuidBytes.toBytes(division), type.name(), UuidBytes.toBytes(author));
		this.jdbc.update("insert into example_ticket_batch (ticket_id, batch_id) values (?, ?)", UuidBytes.toBytes(id),
				UuidBytes.toBytes(batch));
	}

	private Set<UUID> listed(UUID person) {
		return asCaller(person, () -> this.tickets.list().stream().map(ExampleTicket::id).collect(Collectors.toSet()));
	}

	// --- the list: what each role sees ------------------------------------------------------------------------------

	@Test
	@DisplayName("a list holds exactly what the caller's role gives in the place they hold it: department, division for theory, batch for lab and tutorial, everything, or only their own")
	void whatEachRoleSees() throws Exception {
		assertThat(listed(hodOf(Facts.DEPARTMENT_A))).as("an HOD: their department").containsExactlyInAnyOrder(T_THEORY_A,
				T_LAB_A, T_TUTORIAL_A);
		assertThat(listed(hodOf(Facts.DEPARTMENT_B))).as("an HOD of another department").containsExactlyInAnyOrder(T_THEORY_B,
				T_LAB_B);
		assertThat(listed(crOf(Facts.DIVISION_1))).as("a CR: theory lectures of the division").containsExactly(T_THEORY_A);
		assertThat(listed(brOf(Facts.BATCH_1))).as("a BR: lab and tutorial lectures of the batch")
			.containsExactlyInAnyOrder(T_LAB_A, T_TUTORIAL_A);
		assertThat(listed(studentOf(Facts.BATCH_1))).as("a student sees no ticket").isEmpty();
		assertThat(listed(this.faculty1)).as("faculty: the tickets they raised").containsExactlyInAnyOrder(T_THEORY_A, T_LAB_A);
		assertThat(listed(this.faculty2)).containsExactlyInAnyOrder(T_THEORY_B, T_LAB_B, T_TUTORIAL_A);
		assertThat(listed(superHod())).as("a Super HOD: every department").hasSize(5);
		assertThat(listed(somebody())).as("a person with no role: nothing, and no query is made").isEmpty();
	}

	@Test
	@DisplayName("a role removed stops reaching on the next request, and a role in a second place adds it")
	void changesApplyAtOnce() throws Exception {
		UUID person = hodOf(Facts.DEPARTMENT_A);
		assertThat(listed(person)).hasSize(3);

		assign(person, HOD, Facts.DEPARTMENT_B);
		assertThat(listed(person)).as("both departments").hasSize(5);

		unassign(person, HOD, Facts.DEPARTMENT_A);
		assertThat(listed(person)).as("only department B").containsExactlyInAnyOrder(T_THEORY_B, T_LAB_B);

		unassign(person, HOD, Facts.DEPARTMENT_B);
		assertThat(listed(person)).isEmpty();
	}

	@Test
	@DisplayName("the list and the single-record decision agree for every person and every ticket, so a list never shows what a record would refuse, nor hides what it would allow")
	void listAgreesWithRecords() throws Exception {
		List<UUID> everyone = List.of(hodOf(Facts.DEPARTMENT_A), hodOf(Facts.DEPARTMENT_B), crOf(Facts.DIVISION_1),
				crOf(Facts.DIVISION_2), brOf(Facts.BATCH_1), brOf(Facts.BATCH_2), studentOf(Facts.BATCH_1), this.faculty1,
				this.faculty2, superHod(), somebody());
		for (UUID person : everyone) {
			Set<UUID> expected = this.tickets.all().stream()
				.filter((ticket) -> asCaller(person, () -> this.tickets.mayView(ticket)))
				.map(ExampleTicket::id)
				.collect(Collectors.toSet());
			assertThat(listed(person)).as("the list of " + person).isEqualTo(expected);
		}
	}

	// --- one record: 404 when it may not be seen, 403 when it may be seen and not done ------------------------------

	@Test
	@DisplayName("a ticket the caller may not see is answered as not found, so its existence is not revealed")
	void invisibleIsNotFound() throws Exception {
		UUID hodB = hodOf(Facts.DEPARTMENT_B);
		assertThatThrownBy(() -> asCaller(hodB, () -> this.tickets.get(T_THEORY_A))).isInstanceOf(ApiException.class)
			.satisfies((ex) -> assertThat(((ApiException) ex).status().value()).isEqualTo(404));
		assertThat(asCaller(hodOf(Facts.DEPARTMENT_A), () -> this.tickets.get(T_THEORY_A)).id()).isEqualTo(T_THEORY_A);
	}

	@Test
	@DisplayName("a ticket the caller may see but may not change is a 403: a CR sees a theory ticket and cannot revert it, the author and an HOD of the department can")
	void visibleButForbidden() throws Exception {
		UUID cr = crOf(Facts.DIVISION_1);
		assertThatThrownBy(() -> runAs(cr, () -> this.tickets.revert(T_THEORY_A))).isInstanceOf(AccessDeniedException.class);

		runAs(this.faculty1, () -> this.tickets.revert(T_THEORY_A));
		runAs(hodOf(Facts.DEPARTMENT_A), () -> this.tickets.revert(T_LAB_A));
		assertThat(this.tickets.reverted()).containsExactlyInAnyOrder(T_THEORY_A, T_LAB_A);

		// Another faculty member cannot even see it, so it is not found rather than forbidden.
		assertThatThrownBy(() -> runAs(this.faculty2, () -> this.tickets.revert(T_THEORY_A))).isInstanceOf(ApiException.class);
		// The HOD of another department neither.
		assertThatThrownBy(() -> runAs(hodOf(Facts.DEPARTMENT_B), () -> this.tickets.revert(T_TUTORIAL_A)))
			.isInstanceOf(ApiException.class);
	}

	@Test
	@DisplayName("the module's own state rule is checked after the policy and is the module's: the policy does not know a ticket is final")
	void stateRulesAreTheModules() throws Exception {
		runAs(this.faculty1, () -> this.tickets.revert(T_THEORY_A));
		assertThatThrownBy(() -> runAs(this.faculty1, () -> this.tickets.revert(T_THEORY_A))).isInstanceOf(ApiException.class)
			.hasMessageContaining("already reverted");
	}

	// --- the model ---------------------------------------------------------------------------------------------------

	record ExampleTicket(UUID id, UUID departmentId, UUID divisionId, Set<UUID> batchIds, SessionType sessionType,
			UUID authorUserId, boolean permanent) {

		/**
		 * The facts the policy needs, built from the module's own record. In a real module the entity or a small adapter
		 * implements {@link TicketAccessFacts}; the module, not security, knows its people and its places.
		 */
		Facts facts() {
			return new Facts(Facts.TENANT, this.departmentId, this.divisionId, this.batchIds, this.sessionType, Set.of(),
					this.authorUserId, Set.of(), Set.of(), this.permanent, null, Set.of());
		}

	}

	/** What a module writes. Everything about authorization is in {@link #mayView}, {@link #list} and {@link #revert}. */
	static final class ExampleTickets {

		private final Authorization authorization;

		private final JdbcTemplate jdbc;

		private final Set<UUID> reverted = new java.util.HashSet<>();

		ExampleTickets(Authorization authorization, JdbcTemplate jdbc) {
			this.authorization = authorization;
			this.jdbc = jdbc;
		}

		Set<UUID> reverted() {
			return this.reverted;
		}

		/** One record the caller may see, or 404. Check visibility first so a forbidden record is not revealed. */
		ExampleTicket get(UUID id) {
			ExampleTicket ticket = find(id);
			if (ticket == null || !mayView(ticket)) {
				throw ApiException.notFound("example.ticket_not_found", "There is no such ticket.");
			}
			return ticket;
		}

		/** The caller may see it through their place (HOD, CR, BR, Super HOD) or because they are on it (faculty). */
		boolean mayView(ExampleTicket ticket) {
			Facts facts = ticket.facts();
			return this.authorization.can(AcademicPermissions.TICKETS_VIEW_IN_SCOPE, facts)
					|| this.authorization.can(AcademicPermissions.TICKETS_VIEW, facts);
		}

		/** 404 if not visible, 403 if visible and not allowed, then the module's own state rule. */
		void revert(UUID id) {
			ExampleTicket ticket = get(id);
			Facts facts = ticket.facts();
			if (!this.authorization.can(AcademicPermissions.TICKETS_REVERT, facts)
					&& !this.authorization.can(AcademicPermissions.TICKETS_REVERT_IN_SCOPE, facts)) {
				throw new AccessDeniedException("Not allowed to revert this ticket");
			}
			// State rules are the module's. The policy decides who, never whether the ticket is in a state that allows it.
			if (!this.reverted.add(id)) {
				throw ApiException.conflict("example.ticket_final", "This ticket was already reverted.");
			}
		}

		/**
		 * A list is filtered in the query, on the module's own columns, from the caller's reach. Never load everything and
		 * filter in memory, and never page before filtering.
		 */
		List<ExampleTicket> list() {
			Reach inPlace = this.authorization.reach(AcademicPermissions.TICKETS_VIEW_IN_SCOPE);
			boolean mayViewOwn = !this.authorization.reach(AcademicPermissions.TICKETS_VIEW).isEmpty();
			if (inPlace.isEmpty() && !mayViewOwn) {
				return List.of();
			}
			List<Object> args = new ArrayList<>();
			args.add(UuidBytes.toBytes(Facts.TENANT));
			List<String> any = new ArrayList<>();
			if (inPlace.everywhere()) {
				any.add("1 = 1");
			}
			else {
				Set<UUID> departments = inPlace.idsOf(AcademicScopes.DEPARTMENT);
				Set<UUID> divisions = inPlace.idsOf(AcademicScopes.DIVISION);
				Set<UUID> batches = inPlace.idsOf(AcademicScopes.BATCH);
				if (!departments.isEmpty()) {
					any.add("t.department_id in (" + marks(departments) + ")");
					bytes(args, departments);
				}
				if (!divisions.isEmpty()) {
					// The session rule: a division grant gives theory lectures only.
					any.add("(t.division_id in (" + marks(divisions) + ") and t.session_type = 'THEORY')");
					bytes(args, divisions);
				}
				if (!batches.isEmpty()) {
					// A batch grant gives lab and tutorial lectures only, through the table that lists a lecture's batches.
					any.add("(t.session_type in ('LAB', 'TUTORIAL') and exists (select 1 from example_ticket_batch b "
							+ "where b.ticket_id = t.id and b.batch_id in (" + marks(batches) + ")))");
					bytes(args, batches);
				}
			}
			if (mayViewOwn) {
				// A right that comes from a relation is not in the reach; the module adds it from its own data.
				any.add("t.author_user_id = ?");
				args.add(UuidBytes.toBytes(this.authorization.currentUserId()));
			}
			return query("select t.* from example_ticket t where t.tenant_id = ? and (" + String.join(" or ", any) + ")",
					args.toArray());
		}

		List<ExampleTicket> all() {
			return query("select t.* from example_ticket t where t.tenant_id = ?", UuidBytes.toBytes(Facts.TENANT));
		}

		private ExampleTicket find(UUID id) {
			List<ExampleTicket> found = query("select t.* from example_ticket t where t.tenant_id = ? and t.id = ?",
					UuidBytes.toBytes(Facts.TENANT), UuidBytes.toBytes(id));
			return found.isEmpty() ? null : found.get(0);
		}

		private List<ExampleTicket> query(String sql, Object... args) {
			return this.jdbc.query(sql, (rs, row) -> {
				UUID id = UuidBytes.fromBytes(rs.getBytes("id"));
				Set<UUID> batches = Set.copyOf(this.jdbc.query("select batch_id from example_ticket_batch where ticket_id = ?",
						(b, i) -> UuidBytes.fromBytes(b.getBytes("batch_id")), UuidBytes.toBytes(id)));
				return new ExampleTicket(id, UuidBytes.fromBytes(rs.getBytes("department_id")),
						UuidBytes.fromBytes(rs.getBytes("division_id")), batches,
						SessionType.valueOf(rs.getString("session_type")), UuidBytes.fromBytes(rs.getBytes("author_user_id")),
						rs.getBoolean("permanent_change"));
			}, args);
		}

		private static String marks(Collection<UUID> ids) {
			return ids.stream().map((id) -> "?").collect(Collectors.joining(", "));
		}

		private static void bytes(List<Object> args, Collection<UUID> ids) {
			ids.forEach((id) -> args.add(UuidBytes.toBytes(id)));
		}

	}

}
