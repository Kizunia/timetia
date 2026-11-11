package com.kibootia.backend.platform.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States that a use-case method is deliberately open to every signed-in user, and why (ADR 0044). The architecture
 * test requires every public use-case method to carry a check or this marker, so an unprotected method is a decision
 * someone wrote down and a reviewer can read, not an oversight.
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoAuthorization {

	/** Why no check is needed, for example "reads the caller's own profile". */
	String value();

}
