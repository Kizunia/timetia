package com.kibootia.backend.notifications.web;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** How many notifications the caller has not read. */
public record UnreadCountResponse(@Schema(requiredMode = REQUIRED) long count) {

}
