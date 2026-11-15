package com.kibootia.backend.timetable;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.kibootia.backend.timetable.application.TimetableProperties;

/** Switches on the timetable module's settings. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TimetableProperties.class)
class TimetableConfiguration {

}
