package com.kibootia.backend.notes.application;

/** The two sizes a note's picture is kept in; the names are the renditions of {@link NoteAttachmentService#CATEGORY}. */
public enum NoteAttachmentSize {

	/** At most 480 pixels on the long side: lists and the editor. */
	PREVIEW("preview"),

	/** At most 1600 pixels on the long side: looking at the picture. */
	FULL("full");

	private final String rendition;

	NoteAttachmentSize(String rendition) {
		this.rendition = rendition;
	}

	public String rendition() {
		return this.rendition;
	}

}
