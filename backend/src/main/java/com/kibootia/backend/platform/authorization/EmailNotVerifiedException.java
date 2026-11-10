package com.kibootia.backend.platform.authorization;

import org.springframework.security.access.AccessDeniedException;

/**
 * The caller is signed in but has not verified their email address, so the operation is refused (ADR 0041). It is a 403
 * like any denial, with its own problem code so the screen can tell the user what to do instead of showing "not
 * allowed". Only viewing one's own profile, asking for the verification mail again and signing out work before
 * verification, plus operations a module marks with {@link Permission#beforeVerification()}.
 */
public class EmailNotVerifiedException extends AccessDeniedException {

	public EmailNotVerifiedException() {
		super("The email address has not been verified");
	}

}
