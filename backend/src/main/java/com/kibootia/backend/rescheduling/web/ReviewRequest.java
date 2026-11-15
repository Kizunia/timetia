package com.kibootia.backend.rescheduling.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kibootia.backend.rescheduling.application.TicketReviewService.Decision;

/**
 * The body of {@code POST /api/v1/tickets/{id}/review}: a requested reviewer's answer. {@code APPROVE} agrees,
 * {@code ACKNOWLEDGE} says the change has been seen and never blocks, and {@code REJECT} rejects the ticket and needs a
 * {@code reason}.
 */
public record ReviewRequest(@NotNull Decision decision, @Size(max = 500) String reason) {

}
