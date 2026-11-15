package com.kibootia.backend.rescheduling.application;

import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;

/** A person the author asks to review a ticket, and in what capacity. */
public record ReviewerRequest(UUID userId, ReviewerCapacity capacity) {

}
