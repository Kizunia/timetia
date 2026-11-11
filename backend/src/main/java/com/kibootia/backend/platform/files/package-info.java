/**
 * The port other modules use to keep files (ADR 0029, ADR 0039). The {@code files} module implements it when it is
 * enabled; a module that needs it asks for it through an {@code ObjectProvider}, so the application starts without it.
 */
@NamedInterface("files")
package com.kibootia.backend.platform.files;

import org.springframework.modulith.NamedInterface;
