package com.kibootia.backend.platform.web;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.data.domain.Page;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** The body of every list endpoint (ADR 0043): {@code {items, page: {number, size, totalElements, totalPages}}}. */
public record PageResponse<T>(@Schema(requiredMode = REQUIRED) List<T> items,
		@Schema(requiredMode = REQUIRED) PageInfo page) {

	public record PageInfo(
			@Schema(requiredMode = REQUIRED) int number,
			@Schema(requiredMode = REQUIRED) int size,
			@Schema(requiredMode = REQUIRED) long totalElements,
			@Schema(requiredMode = REQUIRED) int totalPages) {

	}

	/** Maps a page of application or entity values to API types and wraps it. */
	public static <E, T> PageResponse<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
		List<T> items = page.getContent().stream().<T>map(mapper).toList();
		return new PageResponse<>(items,
				new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
	}

}
