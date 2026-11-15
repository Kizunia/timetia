package com.kibootia.backend.rescheduling.web;

import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/tickets/{id}/revert}: why the ticket is reverted, if the author wants to say. */
public record RevertRequest(@Size(max = 500) String reason) {

}
