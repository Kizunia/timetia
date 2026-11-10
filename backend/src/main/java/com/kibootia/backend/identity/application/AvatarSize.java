package com.kibootia.backend.identity.application;

/** The sizes an avatar is kept in. The names are the renditions of {@link AvatarService#AVATAR}. */
public enum AvatarSize {

	/** 64 pixels square: lists and the page header. */
	SMALL("small"),

	/** 256 pixels square: the profile page and the user detail screen. */
	LARGE("large");

	private final String rendition;

	AvatarSize(String rendition) {
		this.rendition = rendition;
	}

	public String rendition() {
		return this.rendition;
	}

}
