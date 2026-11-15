package com.kibootia.backend.rescheduling.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/tickets/{id}/submit}: the people to ask to review the draft. The author may ask any
 * combination of the HOD, the CR or BR within their scope, the affected faculty and other faculty.
 */
public record SubmitTicketRequest(@NotEmpty @Size(max = 20) List<@Valid ReviewerBody> reviewers) {

}
