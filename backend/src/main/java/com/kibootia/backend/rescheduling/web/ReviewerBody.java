package com.kibootia.backend.rescheduling.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;

/**
 * A person the author asks to review a ticket, and in what capacity. The capacity says why they are asked and is shown with
 * their answer; what they may do is decided by the role and place they hold.
 */
public record ReviewerBody(@NotNull UUID userId, @NotNull ReviewerCapacity capacity) {

}
