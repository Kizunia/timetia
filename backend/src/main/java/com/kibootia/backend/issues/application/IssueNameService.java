package com.kibootia.backend.issues.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;
import com.kibootia.backend.reference.AcademicDirectory;

/**
 * Looks up the names behind the ids of issues so that the screens never have to: the people, the teachers mentioned and the
 * place. People, teachers and batches are one lookup each for the whole page; divisions are read one by one, once for each
 * distinct division, of which a page holds very few.
 */
@Service
public class IssueNameService {

	private final UserDirectory users;

	private final AcademicDirectory directory;

	IssueNameService(UserDirectory users, AcademicDirectory directory) {
		this.users = users;
		this.directory = directory;
	}

	/** @param details issues the caller has already been allowed to see */
	@Transactional(readOnly = true)
	@NoAuthorization("adds names to issues the caller has already been allowed to see")
	public IssueNames namesFor(Collection<IssueDetail> details) {
		Set<UUID> userIds = new HashSet<>();
		Set<UUID> divisionIds = new HashSet<>();
		Set<UUID> batchIds = new HashSet<>();
		Set<UUID> facultyIds = new HashSet<>();
		for (IssueDetail detail : details) {
			Issue issue = detail.issue();
			userIds.add(issue.openerUserId());
			if (issue.closedByUserId() != null) {
				userIds.add(issue.closedByUserId());
			}
			divisionIds.add(issue.divisionId());
			if (issue.batchId() != null) {
				batchIds.add(issue.batchId());
			}
			facultyIds.addAll(detail.mentionedFacultyIds());
		}
		Map<UUID, String> userNames = this.users.find(userIds)
			.values()
			.stream()
			.collect(Collectors.toMap(UserSummary::id, UserSummary::displayName, (first, second) -> first));
		Map<UUID, String> divisionCodes = new HashMap<>();
		divisionIds.forEach((id) -> this.directory.division(id)
			.ifPresent((division) -> divisionCodes.put(id, division.code())));
		return new IssueNames(userNames, divisionCodes, this.directory.batchCodes(batchIds),
				this.directory.faculties(facultyIds));
	}

}
