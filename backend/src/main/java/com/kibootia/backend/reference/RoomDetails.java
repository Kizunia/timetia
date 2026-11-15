package com.kibootia.backend.reference;

import java.util.UUID;

/** A room as the modules that build on the reference data see it. */
public record RoomDetails(UUID id, String code, String name) {

}
