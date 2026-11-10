package com.kibootia.backend.files.application;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Puts a bean of the {@code files} module in the application only while {@code kibootia.files.enabled} is not
 * {@code false} (ADR 0024). With it off there is no storage client, no job and no listener, and the application starts
 * without storage settings; the table stays migrated, so the schema checks still pass.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "kibootia.files.enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnFilesEnabled {

}
