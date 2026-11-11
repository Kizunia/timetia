package com.kibootia.backend.platform.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Marks a use-case method that needs a permission and nothing about the record (ADR 0044). The check runs before the
 * method, through Spring Security's method security and the decision point. For a rule that needs the loaded record,
 * call {@link Authorization#require(String, Object)} in the method instead.
 *
 * <pre>{@code
 * @RequiresPermission(NotesPermissions.CREATE)
 * public Note create(NewNote note) { ... }
 * }</pre>
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@authorizationChecks.holds('{value}')")
public @interface RequiresPermission {

	/** The permission name, {@code <module>.<resource>.<action>}. */
	String value();

}
