package com.kibootia.backend.platform.web;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;

/**
 * The paging and sorting parameters of a list endpoint (ADR 0043): {@code page} (from 0), {@code size} (default 20,
 * at most 100) and {@code sort=field,asc|desc}. Declare it as {@code @Valid PageQuery query}; out-of-range values then
 * fail with 400 and {@code errors}. Sortable fields are an allow-list per endpoint, so a caller cannot sort by an
 * unindexed or internal column.
 */
public record PageQuery(@Min(0) Integer page, @Min(1) @Max(100) Integer size, String sort) {

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 100;

	/**
	 * @param sortable API field name to entity property for every field the endpoint lets callers sort by
	 * @param defaultSort used when the request has no {@code sort}
	 */
	public Pageable toPageable(Map<String, String> sortable, Sort defaultSort) {
		int number = (this.page != null) ? this.page : 0;
		int pageSize = (this.size != null) ? Math.min(this.size, MAX_SIZE) : DEFAULT_SIZE;
		Sort order = (this.sort == null || this.sort.isBlank()) ? defaultSort : parse(this.sort, sortable);
		// Ties are broken by the time-ordered id so that paging through equal values is stable.
		return PageRequest.of(number, pageSize, order.and(Sort.by(Sort.Direction.ASC, "id")));
	}

	private static Sort parse(String value, Map<String, String> sortable) {
		String[] parts = value.split(",", -1);
		String field = parts[0].trim();
		String direction = (parts.length > 1) ? parts[1].trim().toLowerCase() : "asc";
		if (parts.length > 2 || !sortable.containsKey(field)) {
			throw invalidSort("Sort by one of: " + String.join(", ", sortable.keySet().stream().sorted().toList()));
		}
		if (!direction.equals("asc") && !direction.equals("desc")) {
			throw invalidSort("The sort direction must be asc or desc.");
		}
		return Sort.by(Sort.Direction.fromString(direction), sortable.get(field));
	}

	private static ApiException invalidSort(String message) {
		return ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
				List.of(new ApiFieldError("sort", "unsupported_sort", message)));
	}

}
