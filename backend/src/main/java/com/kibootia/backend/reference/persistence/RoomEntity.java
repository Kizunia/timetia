package com.kibootia.backend.reference.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.SoftDeletableEntity;
import com.kibootia.backend.reference.application.RoomType;

/** The table row of a room. Persistence only (ADR 0004). The type is stored as its name, not an ordinal. */
@Entity
@Table(name = "room")
class RoomEntity extends SoftDeletableEntity {

	@Column(name = "code", nullable = false, length = 20)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "room_type", nullable = false, length = 20)
	private RoomType roomType;

	@Column(name = "capacity")
	private Integer capacity;

	protected RoomEntity() {
	}

	RoomEntity(String code, String name, RoomType roomType, Integer capacity) {
		this.code = code;
		this.name = name;
		this.roomType = roomType;
		this.capacity = capacity;
	}

	String getCode() {
		return this.code;
	}

	String getName() {
		return this.name;
	}

	RoomType getRoomType() {
		return this.roomType;
	}

	Integer getCapacity() {
		return this.capacity;
	}

	void rename(String name) {
		this.name = name;
	}

}
