package com.kibootia.backend.academic;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.platform.authorization.TenantBound;

/**
 * What the policy needs to know about a lecture, an occurrence of one or a record about one. The module that owns the
 * record passes an implementation to {@code Authorization.require(action, facts)}; this module reads it and never changes
 * it. People are user ids, resolved by the module that owns them. A missing value never grants anything.
 */
public interface LectureAccessFacts extends TenantBound {

	/** The department of the lecture's division. */
	UUID departmentId();

	UUID divisionId();

	/**
	 * Every batch the lecture is for. A theory lecture of a division lists all batches of that division, and a shared
	 * course lists the batches of every division that takes it.
	 */
	Set<UUID> batchIds();

	SessionType sessionType();

	/** The faculty who teach the lecture, as user ids. */
	Set<UUID> teachingFacultyUserIds();

	/**
	 * The faculty who teach anything in the lecture's division, as user ids: they see its timetable, not only their own
	 * lectures. Empty when the record does not say, and nothing is then granted.
	 */
	default Set<UUID> divisionFacultyUserIds() {
		return Set.of();
	}

}
