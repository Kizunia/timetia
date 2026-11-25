/**
 * Academic module: the roles, permissions and per-record authorization rules of Timetia (Timetia ADR 0004). It holds no
 * timetable or ticket data. The modules that own those implement the facts interfaces in this package and ask the
 * decision point about their records; this module only decides who may.
 */
@ApplicationModule(displayName = "Academic")
package com.kibootia.backend.academic;

import org.springframework.modulith.ApplicationModule;
