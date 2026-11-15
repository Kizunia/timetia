package com.kibootia.backend.reference.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.kibootia.backend.reference.application.RoomType;

/** The body of {@code POST /api/v1/rooms}. {@code capacity} (seats) is optional. */
public record CreateRoomRequest(@NotBlank @Size(max = 20) String code, @NotBlank @Size(max = 120) String name,
		@NotNull RoomType roomType, @Positive Integer capacity) {

}
