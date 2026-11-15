package com.kibootia.backend.reference;

import java.util.Collection;
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

	Optional<TermDetails> term(UUID termId);

	Optional<DivisionDetails> division(UUID divisionId);

	/** For each of these batches that exists, the division it belongs to. */
	Map<UUID, UUID> divisionsOfBatches(Collection<UUID> batchIds);

	/** The faculty records that belong to this user account; empty when the user is not faculty. */
	Set<UUID> facultyIdsOf(UUID userId);

}
