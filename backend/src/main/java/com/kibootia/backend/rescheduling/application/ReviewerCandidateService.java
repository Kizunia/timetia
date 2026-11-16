package com.kibootia.backend.rescheduling.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.access.ScopeHolders;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;
import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;

/**
 * Who the author of a ticket can ask to review it, so a screen can offer people instead of asking for user ids. The
 * candidates are the ones the ticket's own rules point at: the HODs of the lecture's department, the class representative of
 * its division (theory) or the batch representative of its batch (lab and tutorial), and the faculty members the ticket
 * affects. Super HODs (a role without a place) and other faculty are not listed; they can still be named through the API.
 */
@Service
public class ReviewerCandidateService {

	/** The order the capacities are listed in. */
	private static final List<ReviewerCapacity> ORDER = List.of(ReviewerCapacity.HOD, ReviewerCapacity.CR,
			ReviewerCapacity.BR, ReviewerCapacity.AFFECTED_FACULTY);

	/** A person who may be asked to review, and in which capacity. */
	public record Candidate(UUID userId, String name, ReviewerCapacity capacity) {

	}

	private final TicketSupport support;

	private final ScopeHolders holders;

	private final UserDirectory users;

	private final Authorization authorization;

	ReviewerCandidateService(TicketSupport support, ScopeHolders holders, UserDirectory users,
			Authorization authorization) {
		this.support = support;
		this.holders = holders;
		this.users = users;
		this.authorization = authorization;
	}

	/**
	 * The people the author may ask, HODs first, then CR or BR, then affected faculty, each by name. A person appears once,
	 * in the highest capacity they hold here, because a review can be asked of a person only once. The author never appears,
	 * and neither does anyone who cannot sign in.
	 */
	@Transactional(readOnly = true)
	public List<Candidate> candidates(UUID ticketId) {
		Ticket ticket = this.support.load(ticketId);
		TicketFacts facts = this.support.facts(ticket);
		this.support.requireSeen(facts);
		// Only the author asks for reviews, so only the author may see who can be asked.
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);

		LectureRef lecture = ticket.lecture();
		Map<ReviewerCapacity, Set<UUID>> byCapacity = new EnumMap<>(ReviewerCapacity.class);
		byCapacity.put(ReviewerCapacity.HOD, holdersIn(AcademicScopes.DEPARTMENT, lecture.departmentId(),
				AcademicPermissions.TICKETS_APPROVE_PERMANENT));
		if (lecture.sessionType() == SessionType.THEORY) {
			byCapacity.put(ReviewerCapacity.CR,
					holdersIn(AcademicScopes.DIVISION, lecture.divisionId(), AcademicPermissions.TICKETS_REVIEW));
		}
		else if (lecture.batchId() != null) {
			byCapacity.put(ReviewerCapacity.BR,
					holdersIn(AcademicScopes.BATCH, lecture.batchId(), AcademicPermissions.TICKETS_REVIEW));
		}
		byCapacity.put(ReviewerCapacity.AFFECTED_FACULTY, facts.affectedFacultyUserIds());

		Set<UUID> everyone = new HashSet<>();
		byCapacity.values().forEach(everyone::addAll);
		everyone.remove(ticket.authorUserId());
		Map<UUID, UserSummary> found = this.users.find(everyone);

		List<Candidate> result = new ArrayList<>();
		Set<UUID> listed = new HashSet<>();
		for (ReviewerCapacity capacity : ORDER) {
			for (UUID userId : byCapacity.getOrDefault(capacity, Set.of())) {
				UserSummary user = found.get(userId);
				if (user != null && user.active() && listed.add(userId)) {
					result.add(new Candidate(userId, user.displayName(), capacity));
				}
			}
		}
		result.sort(Comparator.comparingInt((Candidate candidate) -> ORDER.indexOf(candidate.capacity()))
			.thenComparing(Candidate::name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(Candidate::userId));
		return result;
	}

	private Set<UUID> holdersIn(String scopeType, UUID place, String permission) {
		return this.holders.usersHolding(scopeType, Set.of(place), permission).getOrDefault(place, Set.of());
	}

}
