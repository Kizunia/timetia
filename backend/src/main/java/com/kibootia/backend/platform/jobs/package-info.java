/**
 * Scheduled jobs that run on one instance at a time (ADR 0031): the lock provider, and the retention purge that every
 * module with data to expire plugs into.
 */
@NamedInterface("jobs")
package com.kibootia.backend.platform.jobs;

import org.springframework.modulith.NamedInterface;
