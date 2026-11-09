package com.kibootia.backend.platform.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A test-only entity that exercises the persistence base types. */
@Entity
@Table(name = "test_widget")
class TestWidget extends SoftDeletableEntity {

	@Column(name = "name", nullable = false)
	private String name;

	protected TestWidget() {
	}

	TestWidget(String name) {
		this.name = name;
	}

	String getName() {
		return this.name;
	}

	void setName(String name) {
		this.name = name;
	}

}
