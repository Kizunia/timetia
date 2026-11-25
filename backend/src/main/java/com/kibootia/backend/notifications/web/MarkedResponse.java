package com.kibootia.backend.notifications.web;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** How many notifications were marked read. */
public record MarkedResponse(@Schema(requiredMode = REQUIRED) int marked) {

}
