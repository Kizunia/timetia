package com.kibootia.backend.timetable.application;

/**
 * Where a timetable version is in its life. A draft is being arranged and has no effect; publishing it makes it the
 * timetable from its effective date and fixes it: a published version is never edited.
 */
public enum VersionStatus {

	DRAFT, PUBLISHED

}
