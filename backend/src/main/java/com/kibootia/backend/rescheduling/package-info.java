/**
 * Rescheduling module: tickets that change a lecture of the live timetable, and the rules of their life (Timetia ADR 0001
 * and the lecture-rescheduling module). A ticket is a proposal that is checked for conflicts, and changes nothing until it
 * is applied. It owns tickets, the changes they make and the conflict checks made on them; it reads the lectures, and
 * changes them, only through {@link com.kibootia.backend.timetable.LectureCalendar}, and reads the reference data only through
 * {@link com.kibootia.backend.reference.AcademicDirectory}. Who may do what is decided by the policy of the
 * {@code academic} module from the facts this module builds ({@link com.kibootia.backend.academic.TicketAccessFacts}).
 */
@ApplicationModule(displayName = "Rescheduling")
package com.kibootia.backend.rescheduling;

import org.springframework.modulith.ApplicationModule;
