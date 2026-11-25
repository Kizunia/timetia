/**
 * Timetable module: a division's manually arranged base timetable (Timetia ADR 0001), published as dated lectures, and
 * the live view of them. It owns versions, weekly entries and the lectures on each date; it reads the reference data only
 * through {@link com.kibootia.backend.reference.AcademicDirectory}, and builds the facts the authorization policy needs
 * ({@link com.kibootia.backend.academic.LectureAccessFacts}) for the lectures it owns. Changes to a lecture are the
 * rescheduling module's business and are laid over this base, never written into it.
 */
@ApplicationModule(displayName = "Timetable")
package com.kibootia.backend.timetable;

import org.springframework.modulith.ApplicationModule;
