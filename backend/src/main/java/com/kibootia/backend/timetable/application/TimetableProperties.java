package com.kibootia.backend.timetable.application;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Timetable settings.
 *
 * @param timeZone the time zone of the campus, as a name such as {@code Asia/Kolkata}. Lectures are held at hours of the day
 *        with no zone of their own, so this is what says when one starts: a lecture that has started can no longer be
 *        changed. The server's own zone is not used, because a server need not be where the campus is. Asia/Kolkata by
 *        default. An unknown name stops the application at start-up.
 */
@ConfigurationProperties("kibootia.timetable")
@Validated
public record TimetableProperties(@DefaultValue("Asia/Kolkata") @NotBlank String timeZone) {

	public TimetableProperties {
		ZoneId.of(timeZone);
	}

	public ZoneId zone() {
		return ZoneId.of(this.timeZone);
	}

}
