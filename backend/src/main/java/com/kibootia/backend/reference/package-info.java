/**
 * Reference data: the campus structure (departments, divisions, batches) and the rest of the manually seeded
 * academic data (Timetia ADR 0001). It owns the <em>places</em> a Timetia role is assigned in, so it supplies the
 * {@link com.kibootia.backend.platform.authorization.ScopeDirectory} the access module asks before a scoped role may be
 * given; without this module no scoped role can be assigned (integration contract). The timetable and rescheduling
 * modules read this data through its published ports, never its tables.
 */
@ApplicationModule(displayName = "Reference data")
package com.kibootia.backend.reference;

import org.springframework.modulith.ApplicationModule;
