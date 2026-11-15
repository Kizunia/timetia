package com.kibootia.backend.rescheduling;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who sees which tickets (the role and scope model): the people on a ticket, and the roles whose place covers its lecture,
 * with the session rule: a CR sees the theory tickets of the division and a BR the lab and tutorial tickets of the batch,
 * a department's HOD sees all of its tickets, and a student, who only reads the timetable, sees none. As for the live
 * timetable, the list and the single-ticket decision must agree for every person, so a list never shows what a ticket would
 * refuse, nor hides what it would allow.
 *
 * <p>Four tickets: {@code theory} (Rao, OOP theory of CSE-A), {@code labA2} (Roy, a lab of batch A2), {@code labA1} (Rao,
 * cancelling a lab of batch A1) and {@code thermo} (Mehta, Thermodynamics theory of ME-A).
 */
class TicketVisibilityApiIT extends ReschedulingApiTest {

	private String theory;

	private String labA2Ticket;

	private String labA1Ticket;

	private String thermo;

	@BeforeEach
	void raiseFourTickets() throws Exception {
		this.theory = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101));
		this.labA2Ticket = raise(ROY_USER, move(lecture(this.labA2, "2026-07-07"), "2026-07-09", 9, 11, this.room102));
		this.labA1Ticket = raise(RAO_USER, cancel(lecture(this.labA1, "2026-07-07"), "Lab is not ready"));
		this.thermo = raise(MEHTA_USER, move(lecture(this.theoryThermo, "2026-07-06"), "2026-07-08", 9, 10, this.room201));
	}

	@Test
	@DisplayName("a person sees the tickets they are on and those their role covers: department, theory of a division, lab of a batch, or everything")
	void whatEachPersonSees() throws Exception {
		assertThat(tickets(user(RAO_USER))).as("a teacher: the tickets on their own lectures").containsExactlyInAnyOrder(this.theory,
				this.labA1Ticket);
		assertThat(tickets(user(ROY_USER))).containsExactly(this.labA2Ticket);
		assertThat(tickets(user(MEHTA_USER))).containsExactly(this.thermo);
		assertThat(tickets(user(personWith("HOD", this.deptCse)))).as("an HOD: every ticket of their department")
			.containsExactlyInAnyOrder(this.theory, this.labA2Ticket, this.labA1Ticket);
		assertThat(tickets(user(personWith("HOD", this.deptMe)))).containsExactly(this.thermo);
		assertThat(tickets(user(personWith("Super HOD", null)))).as("a Super HOD: every department").hasSize(4);
		assertThat(tickets(user(personWith("CR", this.divisionCseA)))).as("a CR: the theory tickets of the division")
			.containsExactly(this.theory);
		assertThat(tickets(user(personWith("CR", this.divisionMeA)))).containsExactly(this.thermo);
		assertThat(tickets(user(personWith("BR", this.batchA1)))).as("a BR: the lab tickets of the batch")
			.containsExactly(this.labA1Ticket);
		assertThat(tickets(user(personWith("BR", this.batchA2)))).containsExactly(this.labA2Ticket);
		assertThat(tickets(user(personWith("Student", this.batchA1)))).as("a student sees the result, not the ticket").isEmpty();
		assertThat(tickets(admin())).as("the Super Admin holds no ticket permission").isEmpty();
		assertThat(tickets(user(somebody()))).isEmpty();
	}

	@Test
	@DisplayName("a ticket the caller may not see is answered as not found")
	void anInvisibleTicketIsNotFound() throws Exception {
		get(user(ROY_USER), "/api/v1/tickets/" + this.theory).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_found"));
		get(user(personWith("HOD", this.deptMe)), "/api/v1/tickets/" + this.theory).andExpect(status().isNotFound());
		get(user(personWith("BR", this.batchA1)), "/api/v1/tickets/" + this.theory).andExpect(status().isNotFound());
		get(user(personWith("CR", this.divisionCseA)), "/api/v1/tickets/" + this.labA1Ticket).andExpect(status().isNotFound());
		get(user(personWith("Student", this.batchA1)), "/api/v1/tickets/" + this.theory).andExpect(status().isNotFound());
		get(admin(), "/api/v1/tickets/" + this.theory).andExpect(status().isNotFound());
		get(user(personWith("HOD", this.deptCse)), "/api/v1/tickets/" + this.theory).andExpect(status().isOk());
	}

	@Test
	@DisplayName("the list and the single-ticket decision agree for every person and every ticket, so a list never shows what a ticket would refuse, nor hides what it would allow")
	void listAgreesWithRecords() throws Exception {
		Set<String> every = Set.of(this.theory, this.labA2Ticket, this.labA1Ticket, this.thermo);
		UUID both = personWith("HOD", this.deptMe);
		assign(both, "BR", this.batchA1);
		Map<String, RequestPostProcessor> people = new LinkedHashMap<>();
		people.put("admin", admin());
		people.put("rao", user(RAO_USER));
		people.put("roy", user(ROY_USER));
		people.put("mehta", user(MEHTA_USER));
		people.put("hod cse", user(personWith("HOD", this.deptCse)));
		people.put("hod me", user(personWith("HOD", this.deptMe)));
		people.put("super hod", user(personWith("Super HOD", null)));
		people.put("cr cse-a", user(personWith("CR", this.divisionCseA)));
		people.put("cr me-a", user(personWith("CR", this.divisionMeA)));
		people.put("br a1", user(personWith("BR", this.batchA1)));
		people.put("br a2", user(personWith("BR", this.batchA2)));
		people.put("student", user(personWith("Student", this.batchA1)));
		people.put("hod me and br a1", user(both));
		people.put("nobody", user(somebody()));

		for (Map.Entry<String, RequestPostProcessor> person : people.entrySet()) {
			Set<String> allowed = new HashSet<>();
			for (String ticket : every) {
				int answer = get(person.getValue(), "/api/v1/tickets/" + ticket).andReturn().getResponse().getStatus();
				assertThat(answer).as(person.getKey() + " on " + ticket + " is either allowed or not found").isIn(200, 404);
				if (answer == 200) {
					allowed.add(ticket);
				}
			}
			assertThat(tickets(person.getValue())).as("the list of " + person.getKey()).isEqualTo(allowed);
		}
	}

	@Test
	@DisplayName("a list is narrowed by state, by the lecture a ticket concerns, and to the caller's own tickets, and never beyond what they may see")
	void filters() throws Exception {
		UUID hod = personWith("HOD", this.deptCse);
		applyOk(RAO_USER, this.labA1Ticket);
		String hodsOwn = raise(hod, cancel(lecture(this.theoryDbms, "2026-07-13"), "Roy is on leave"));

		assertThat(listedTickets(user(hod), "&status=APPLIED")).containsExactly(this.labA1Ticket);
		assertThat(listedTickets(user(hod), "&status=DRAFT")).containsExactlyInAnyOrder(this.theory, this.labA2Ticket, hodsOwn);
		assertThat(listedTickets(user(hod), "&mine=true")).containsExactly(hodsOwn);
		assertThat(listedTickets(user(hod), "&lecture=" + lecture(this.theoryOop, "2026-07-06"))).containsExactly(this.theory);
		// A filter cannot widen what the caller may see.
		assertThat(listedTickets(user(personWith("HOD", this.deptMe)), "&lecture=" + lecture(this.theoryOop, "2026-07-06")))
			.isEmpty();
		assertThat(listedTickets(user(RAO_USER), "&mine=true")).containsExactlyInAnyOrder(this.theory, this.labA1Ticket);
	}

	@Test
	@DisplayName("the list is paged after it is filtered, so a page and its total are right")
	void pagesAfterFiltering() throws Exception {
		UUID hod = personWith("HOD", this.deptCse);

		get(user(hod), "/api/v1/tickets?size=2&page=0").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.page.totalElements").value(3))
			.andExpect(jsonPath("$.page.totalPages").value(2));
		get(user(hod), "/api/v1/tickets?size=2&page=1").andExpect(jsonPath("$.items.length()").value(1));
	}

	private Set<String> tickets(RequestPostProcessor who) throws Exception {
		return listedTickets(who, "");
	}

}
