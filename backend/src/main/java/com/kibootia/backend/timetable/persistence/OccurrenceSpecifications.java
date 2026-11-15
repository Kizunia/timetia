package com.kibootia.backend.timetable.persistence;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.kibootia.backend.timetable.application.OccurrenceQuery;
import com.kibootia.backend.timetable.application.OccurrenceQuery.Visibility;

/**
 * Turns an {@link OccurrenceQuery} into one query condition on the lecture's own columns. Where the caller may see
 * lectures is part of the same condition as the date range and the filters, so the database filters before it pages and a
 * page and its total are right. An empty set of places adds no condition for that kind rather than an {@code in ()}, and
 * a visibility that allows nothing never reaches here (the service answers an empty page first).
 */
final class OccurrenceSpecifications {

	private OccurrenceSpecifications() {
	}

	static Specification<OccurrenceEntity> matching(OccurrenceQuery query) {
		return (root, criteria, builder) -> {
			List<Predicate> all = new ArrayList<>();
			all.add(builder.equal(root.get("recordStatus"), OccurrenceStatus.ACTIVE));
			all.add(builder.between(root.get("effectiveDate"), query.from(), query.to()));
			if (query.division() != null) {
				all.add(builder.equal(root.get("divisionId"), query.division()));
			}
			if (query.faculty() != null) {
				all.add(builder.equal(root.get("facultyId"), query.faculty()));
			}
			if (query.batch() != null) {
				// A lab or tutorial names its batch; a theory lecture is for every batch of its division.
				Predicate own = builder.equal(root.get("batchId"), query.batch());
				Predicate wholeDivision = (query.batchDivision() == null) ? builder.disjunction()
						: builder.and(builder.isNull(root.get("batchId")),
								builder.equal(root.get("divisionId"), query.batchDivision()));
				all.add(builder.or(own, wholeDivision));
			}
			Visibility visibility = query.visibility();
			if (!visibility.everywhere()) {
				List<Predicate> any = new ArrayList<>();
				if (!visibility.departments().isEmpty()) {
					any.add(root.get("departmentId").in(visibility.departments()));
				}
				if (!visibility.divisions().isEmpty()) {
					any.add(root.get("divisionId").in(visibility.divisions()));
				}
				if (!visibility.batches().isEmpty()) {
					any.add(root.get("batchId").in(visibility.batches()));
				}
				if (!visibility.divisionsOfBatches().isEmpty()) {
					any.add(builder.and(builder.isNull(root.get("batchId")),
							root.get("divisionId").in(visibility.divisionsOfBatches())));
				}
				if (!visibility.faculty().isEmpty()) {
					any.add(root.get("facultyId").in(visibility.faculty()));
				}
				all.add(any.isEmpty() ? builder.disjunction() : builder.or(any.toArray(new Predicate[0])));
			}
			return builder.and(all.toArray(new Predicate[0]));
		};
	}

}
