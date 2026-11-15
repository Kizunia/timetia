package com.kibootia.backend.rescheduling.domain;

/**
 * In what capacity a person was asked to review (data model {@code reviewer_capacity}). It says why they were asked and
 * is shown with their answer. What they may do is decided by the role and place they hold, not by this: asking someone as an
 * HOD does not make them one.
 */
public enum ReviewerCapacity {

	HOD, CR, BR, AFFECTED_FACULTY, OTHER_FACULTY

}
