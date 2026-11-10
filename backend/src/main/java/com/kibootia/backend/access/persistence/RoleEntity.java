package com.kibootia.backend.access.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.access.application.Role;
import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/**
 * The table row of a role. Persistence only: it never leaves this package (ADR 0004). What the role allows and who
 * holds it are in link tables the store reads with SQL, because they are plain rows with no history of their own.
 */
@Entity
@Table(name = "role")
class RoleEntity extends SoftDeletableEntity {

	@Column(name = "name", nullable = false, length = 80)
	private String name;

	@Column(name = "description", nullable = false, length = 255)
	private String description;

	/** The name of a {@link Role.BuiltIn}, or null. A plain string, so schema validation needs no native enum type. */
	@Column(name = "built_in", length = 20)
	private String builtIn;

	protected RoleEntity() {
	}

	RoleEntity(String name, String description, Role.BuiltIn builtIn) {
		this.name = name;
		this.description = description;
		this.builtIn = (builtIn != null) ? builtIn.name() : null;
	}

	String getName() {
		return this.name;
	}

	String getDescription() {
		return this.description;
	}

	Role.BuiltIn getBuiltIn() {
		return (this.builtIn != null) ? Role.BuiltIn.valueOf(this.builtIn) : null;
	}

	void change(String name, String description) {
		this.name = name;
		this.description = description;
	}

}
