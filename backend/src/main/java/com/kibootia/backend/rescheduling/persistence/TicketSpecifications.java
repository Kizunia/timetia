package com.kibootia.backend.rescheduling.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.rescheduling.application.TicketQuery;
import com.kibootia.backend.rescheduling.application.TicketQuery.Visibility;

/**
 * Turns a {@link TicketQuery} into one query condition on the ticket's own columns. Where the caller may see tickets is part
 * of the same condition as the filters, so the database filters before it pages and a page and its total are right. The
 * session rule of the model is part of it: a division's scope gives its theory tickets and a batch's scope its lab and
 * tutorial tickets, while a department's gives every ticket. A visibility that allows nothing never reaches here.
 */
final class TicketSpecifications {

	private TicketSpecifications() {
	}

	static Specification<TicketEntity> matching(TicketQuery query) {
		return (root, criteria, builder) -> {
			List<Predicate> all = new ArrayList<>();
			if (query.status() != null) {
				all.add(builder.equal(root.get("status"), query.status()));
			}
			if (query.authoredBy() != null) {
				all.add(builder.equal(root.get("authorUserId"), query.authoredBy()));
			}
			if (query.lectureId() != null) {
				Subquery<UUID> concerning = criteria.subquery(UUID.class);
				Root<TicketChangeEntity> change = concerning.from(TicketChangeEntity.class);
				concerning.select(change.get("ticketId"))
					.where(builder.or(builder.equal(change.get("sourceOccurrenceId"), query.lectureId()),
							builder.equal(change.get("createdOccurrenceId"), query.lectureId())));
				all.add(root.get("id").in(concerning));
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
				if (visibility.writtenBy() != null) {
					any.add(builder.equal(root.get("authorUserId"), visibility.writtenBy()));
				}
				if (!visibility.teachingFaculty().isEmpty()) {
					any.add(root.get("facultyId").in(visibility.teachingFaculty()));
					// So is a faculty member whose lecture is changed along with it (an exchange), and one who takes a lecture
					// as a substitute.
					Subquery<UUID> involved = criteria.subquery(UUID.class);
					Root<TicketChangeEntity> item = involved.from(TicketChangeEntity.class);
					involved.select(item.get("ticketId"))
						.where(builder.equal(item.get("ticketId"), root.get("id")),
								builder.or(item.get("facultyId").in(visibility.teachingFaculty()),
										item.get("targetFacultyId").in(visibility.teachingFaculty())));
					any.add(builder.exists(involved));
				}
				if (visibility.reviewing() != null) {
					// Asked to review it, in the revision it is in now: an earlier revision's request no longer counts.
					Subquery<UUID> asked = criteria.subquery(UUID.class);
					Root<TicketReviewEntity> review = asked.from(TicketReviewEntity.class);
					asked.select(review.get("ticketId"))
						.where(builder.equal(review.get("reviewerUserId"), visibility.reviewing()),
								builder.equal(review.get("ticketId"), root.get("id")),
								builder.equal(review.get("ticketRevisionNo"), root.get("revisionNo")));
					any.add(builder.exists(asked));
				}
				all.add(any.isEmpty() ? builder.disjunction() : builder.or(any.toArray(new Predicate[0])));
			}
			return builder.and(all.toArray(new Predicate[0]));
		};
	}

}
