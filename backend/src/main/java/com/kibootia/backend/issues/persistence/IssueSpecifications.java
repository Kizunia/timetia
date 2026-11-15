package com.kibootia.backend.issues.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.issues.application.IssueQuery;
import com.kibootia.backend.issues.application.IssueQuery.Visibility;

/**
 * The conditions of a list of issues, built from what the caller may see. They are the same rules the policy applies to one
 * issue, written as conditions on the issue's own columns, so the list and the single issue never disagree. A visibility that
 * allows nothing never reaches here.
 */
final class IssueSpecifications {

	private IssueSpecifications() {
	}

	static Specification<IssueEntity> matching(IssueQuery query) {
		return (root, criteria, builder) -> {
			List<Predicate> all = new ArrayList<>();
			if (query.status() != null) {
				all.add(builder.equal(root.get("status"), query.status()));
			}
			if (query.openedBy() != null) {
				all.add(builder.equal(root.get("openerUserId"), query.openedBy()));
			}
			Visibility visibility = query.visibility();
			if (!visibility.everywhere()) {
				List<Predicate> any = new ArrayList<>();
				if (!visibility.departments().isEmpty()) {
					any.add(root.get("departmentId").in(visibility.departments()));
				}
				if (!visibility.theoryOfDivisions().isEmpty()) {
					any.add(builder.and(root.get("divisionId").in(visibility.theoryOfDivisions()),
							builder.equal(root.get("sessionType"), SessionType.THEORY)));
				}
				if (!visibility.labsOfBatches().isEmpty()) {
					any.add(builder.and(root.get("batchId").in(visibility.labsOfBatches()),
							root.get("sessionType").in(SessionType.LAB, SessionType.TUTORIAL)));
				}
				if (visibility.openedBy() != null) {
					any.add(builder.equal(root.get("openerUserId"), visibility.openedBy()));
				}
				if (!visibility.mentionedFaculty().isEmpty()) {
					Subquery<UUID> mentioning = criteria.subquery(UUID.class);
					Root<IssueMentionEntity> mention = mentioning.from(IssueMentionEntity.class);
					mentioning.select(mention.get("issueId"))
						.where(builder.equal(mention.get("issueId"), root.get("id")),
								mention.get("mentionedFacultyId").in(visibility.mentionedFaculty()));
					any.add(builder.exists(mentioning));
				}
				all.add(any.isEmpty() ? builder.disjunction() : builder.or(any.toArray(new Predicate[0])));
			}
			return builder.and(all.toArray(new Predicate[0]));
		};
	}

}
