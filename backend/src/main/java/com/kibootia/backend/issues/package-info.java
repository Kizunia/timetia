/**
 * Issues module: what a class asks for, in the way of a GitHub issue (rules: issues from CR and BR). A class representative
 * (theory) or batch representative (labs and tutorials) opens an issue in their place, mentions the faculty it concerns, and
 * the faculty who agree raise a ticket themselves. An issue never changes the timetable; it can be linked to the ticket that
 * answers it. It owns issues, their mentions and their links; it reads the reference data only through
 * {@link com.kibootia.backend.reference.AcademicDirectory}, tickets only through
 * {@link com.kibootia.backend.rescheduling.TicketVisibility}, and tells the people it mentions through
 * {@link com.kibootia.backend.issues.IssueMentioned}. Who may do what is decided by the policy of the {@code academic}
 * module from the facts this module builds ({@link com.kibootia.backend.academic.IssueAccessFacts}).
 */
@ApplicationModule(displayName = "Issues")
package com.kibootia.backend.issues;

import org.springframework.modulith.ApplicationModule;
