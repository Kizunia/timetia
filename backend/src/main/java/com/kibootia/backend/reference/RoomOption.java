package com.kibootia.backend.reference;

import java.util.UUID;

/**
 * A room a lecture could be held in, with what decides whether it suits one: its kind and its seats. The kind is the name of
 * the room type ({@code CLASSROOM}, {@code TUTORIAL_ROOM} or {@code LAB}), as text, because the type itself is not published.
 *
 * @param capacity the number of seats, or null when it was not recorded
 */
public record RoomOption(UUID id, String code, String name, String roomType, Integer capacity) {

}
