/**
 * Resource server configuration (ADR 0007, ADR 0008). Only the {@code RequestGate} port is visible to other modules;
 * the rest is private to the platform.
 */
@NamedInterface("security")
package com.kibootia.backend.platform.security;

import org.springframework.modulith.NamedInterface;
