package com.kibootia.backend.reference;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the modules that build on the reference data (the timetable, rescheduling, issues) may ask it, and the only way
 * they may: they never read its tables or its internals. Every answer is about live rows of the current tenant; an id
 * whose row was deleted is simply absent from the answer, and the caller decides what that means for its own record.
 *
 * <p>The lookups are read-only and are made inside a request the caller has already authorized, so they check no
 * permission of their own.
 */
public interface AcademicDirectory {

	/**
	 * The teaching assignments among these ids, with everything a lecture shows and the authorization policy needs. An
	 * assignment whose division, subject or faculty no longer exists is left out.
	 */
	Map<UUID, AssignmentDetails> assignments(Collection<UUID> assignmentIds);

	Map<UUID, RoomDetails> rooms(Collection<UUID> roomIds);

	/** Every room of the campus (at most 500), by code, with its kind and seats. */
	List<RoomOption> roomOptions();

	/** The faculty members among these ids; one that was deleted is absent. */
	Map<UUID, FacultyDetails> faculties(Collection<UUID> facultyIds);

	Optional<TermDetails> term(UUID termId);

	Optional<DivisionDetails> division(UUID divisionId);

	/** For each of these batches that exists, the division it belongs to. */
	Map<UUID, UUID> divisionsOfBatches(Collection<UUID> batchIds);

	/** The codes of the batches among these ids; one that was deleted is absent. */
	Map<UUID, String> batchCodes(Collection<UUID> batchIds);

	/** The divisions in which this user, as a faculty member, has a teaching assignment; empty when they teach in none. */
	Set<UUID> divisionIdsTaughtBy(UUID userId);

	/**
	 * For each of these divisions, the accounts of the faculty who have a teaching assignment in it. A faculty member with no
	 * account is left out. A division nobody teaches in is absent.
	 */
	Map<UUID, Set<UUID>> facultyUserIdsOfDivisions(Collection<UUID> divisionIds);

	/** The faculty records that belong to this user account; empty when the user is not faculty. */
	Set<UUID> facultyIdsOf(UUID userId);

}
