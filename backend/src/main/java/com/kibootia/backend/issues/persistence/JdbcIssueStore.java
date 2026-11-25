package com.kibootia.backend.issues.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.issues.application.Issue;
import com.kibootia.backend.issues.application.IssueQuery;
import com.kibootia.backend.issues.application.IssueQuery.Visibility;
import com.kibootia.backend.issues.application.IssueStore;
import com.kibootia.backend.issues.application.NewIssue;
import com.kibootia.backend.issues.domain.IssueStatus;
import com.kibootia.backend.platform.persistence.JdbcSupport;

/**
 * Keeps issues, their mentions and their ticket links in MySQL with hand-written SQL on {@link JdbcSupport}. Every
 * statement names {@code tenant_id}, also inside a subquery, because no framework adds it.
 *
 * <p>The list is the one query that is specific to this module: {@link #visibilityCondition} writes the rules the policy
 * applies to one issue as conditions on the issue's own columns, so the list and a single issue never disagree.
 */
@Component
class JdbcIssueStore extends JdbcSupport implements IssueStore {

	private static final Map<String, String> SORT_COLUMNS = Map.of("createdAt", "created_at");

	private static final String COLUMNS = """
			i.id, i.tenant_id, i.version, i.created_at, i.updated_at, i.session_type, i.status, i.department_id,
			i.division_id, i.batch_id, i.title, i.description, i.opener_user_id, i.closed_at, i.closed_by_user_id
			""";

	private static final String INSERT_ISSUE = """
			INSERT INTO issue (id, tenant_id, version, created_at, created_by, updated_at, updated_by, session_type, status,
			                   department_id, division_id, batch_id, title, description, opener_user_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_ISSUE = "SELECT " + COLUMNS + """
			FROM issue i
			WHERE i.tenant_id = ? AND i.id = ?
			""";

	// Only the state of an issue changes; where it was opened, by whom and what it says never do.
	private static final String UPDATE_ISSUE = """
			UPDATE issue
			SET status = ?, closed_at = ?, closed_by_user_id = ?, version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	// The issues a caller may see, narrowed by their filters. %s is the extra conditions.
	private static final String SEARCH_ISSUES = "SELECT " + COLUMNS + """
			FROM issue i
			WHERE i.tenant_id = ?%s""";

	private static final String COUNT_ISSUES = """
			SELECT COUNT(*) FROM issue i
			WHERE i.tenant_id = ?%s
			""";

	private static final String INSERT_MENTION = """
			INSERT INTO issue_mention (id, tenant_id, version, created_at, created_by, updated_at, updated_by, issue_id,
			                           mentioned_faculty_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?)
			""";

	private static final String MENTION_EXISTS = """
			SELECT COUNT(*) FROM issue_mention WHERE tenant_id = ? AND issue_id = ? AND mentioned_faculty_id = ?
			""";

	private static final String MENTIONS_OF = """
			SELECT issue_id, mentioned_faculty_id FROM issue_mention WHERE tenant_id = ? AND issue_id IN (%s)
			""";

	private static final String INSERT_LINK = """
			INSERT INTO issue_ticket_link (id, tenant_id, version, created_at, created_by, updated_at, updated_by, issue_id,
			                               ticket_id, linked_by_user_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String LINK_EXISTS = """
			SELECT COUNT(*) FROM issue_ticket_link WHERE tenant_id = ? AND issue_id = ? AND ticket_id = ?
			""";

	private static final String LINKS_OF = """
			SELECT issue_id, ticket_id FROM issue_ticket_link WHERE tenant_id = ? AND issue_id IN (%s)
			""";

	JdbcIssueStore(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Issue insert(NewIssue issue) {
		UUID id = newId();
		UUID tenant = tenantForWrite();
		Instant now = now();
		execute(INSERT_ISSUE, id, tenant, now, actor(), now, actor(), issue.sessionType(), IssueStatus.OPEN,
				issue.departmentId(), issue.divisionId(), issue.batchId(), issue.title(), issue.description(),
				issue.openerUserId());
		issue.mentionedFacultyIds().forEach((faculty) -> insertMention(id, faculty));
		return new Issue(id, tenant, issue.sessionType(), IssueStatus.OPEN, issue.departmentId(), issue.divisionId(),
				issue.batchId(), issue.title(), issue.description(), issue.openerUserId(), null, null, 0, now, now);
	}

	@Override
	public Optional<Issue> find(UUID id) {
		return queryOne(FIND_ISSUE, JdbcIssueStore::toIssue, tenant(), id);
	}

	@Override
	public Issue update(Issue issue) {
		Issue current = find(issue.id()).orElseThrow();
		if (current.version() != issue.version()) {
			throw staleVersion("Issue", issue.id());
		}
		Instant now = now();
		int changed = execute(UPDATE_ISSUE, issue.status(), issue.closedAt(), issue.closedByUserId(), now, actor(), tenant(),
				issue.id(), issue.version());
		requireOneRow(changed, "Issue", issue.id());
		return new Issue(current.id(), current.tenantId(), current.sessionType(), issue.status(), current.departmentId(),
				current.divisionId(), current.batchId(), current.title(), current.description(), current.openerUserId(),
				issue.closedAt(), issue.closedByUserId(), current.version() + 1, current.createdAt(), now);
	}

	@Override
	public Set<UUID> mentionedFacultyIds(UUID issueId) {
		return mentionedFacultyIdsOf(Set.of(issueId)).getOrDefault(issueId, Set.of());
	}

	@Override
	public Map<UUID, Set<UUID>> mentionedFacultyIdsOf(Collection<UUID> issueIds) {
		return pairsOf(MENTIONS_OF, "mentioned_faculty_id", issueIds);
	}

	@Override
	public Set<UUID> linkedTicketIds(UUID issueId) {
		return linkedTicketIdsOf(Set.of(issueId)).getOrDefault(issueId, Set.of());
	}

	@Override
	public Map<UUID, Set<UUID>> linkedTicketIdsOf(Collection<UUID> issueIds) {
		return pairsOf(LINKS_OF, "ticket_id", issueIds);
	}

	@Override
	public boolean addMention(UUID issueId, UUID facultyId) {
		if (queryCount(MENTION_EXISTS, tenant(), issueId, facultyId) > 0) {
			return false;
		}
		insertMention(issueId, facultyId);
		return true;
	}

	@Override
	public boolean addLink(UUID issueId, UUID ticketId, UUID linkedByUserId) {
		if (queryCount(LINK_EXISTS, tenant(), issueId, ticketId) > 0) {
			return false;
		}
		Instant now = now();
		execute(INSERT_LINK, newId(), tenantForWrite(), now, actor(), now, actor(), issueId, ticketId, linkedByUserId);
		return true;
	}

	@Override
	public Page<Issue> search(IssueQuery query, Pageable pageable) {
		StringBuilder conditions = new StringBuilder();
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		if (query.status() != null) {
			conditions.append(" AND i.status = ?");
			parameters.add(query.status());
		}
		if (query.openedBy() != null) {
			conditions.append(" AND i.opener_user_id = ?");
			parameters.add(query.openedBy());
		}
		if (!query.visibility().everywhere()) {
			conditions.append(visibilityCondition(query.visibility(), parameters));
		}
		String where = conditions.toString();
		String select = SEARCH_ISSUES.formatted(where) + orderBy(pageable.getSort(), SORT_COLUMNS);
		return queryPage(select, COUNT_ISSUES.formatted(where), JdbcIssueStore::toIssue, pageable, parameters.toArray());
	}

	/**
	 * An issue is visible when any one of the ways the caller may see it holds (see {@link Visibility}). The parameters are
	 * added in the order their {@code ?} appear. No way at all allows nothing.
	 */
	private static String visibilityCondition(Visibility visibility, List<Object> parameters) {
		List<String> any = new ArrayList<>();
		if (!visibility.departments().isEmpty()) {
			any.add("i.department_id IN (" + placeholders(visibility.departments().size()) + ")");
			parameters.addAll(visibility.departments());
		}
		if (!visibility.theoryOfDivisions().isEmpty()) {
			any.add("(i.division_id IN (" + placeholders(visibility.theoryOfDivisions().size()) + ") AND i.session_type = ?)");
			parameters.addAll(visibility.theoryOfDivisions());
			parameters.add(SessionType.THEORY);
		}
		if (!visibility.labsOfBatches().isEmpty()) {
			any.add("(i.batch_id IN (" + placeholders(visibility.labsOfBatches().size()) + ") AND i.session_type IN (?, ?))");
			parameters.addAll(visibility.labsOfBatches());
			parameters.add(SessionType.LAB);
			parameters.add(SessionType.TUTORIAL);
		}
		if (visibility.openedBy() != null) {
			any.add("i.opener_user_id = ?");
			parameters.add(visibility.openedBy());
		}
		if (!visibility.mentionedFaculty().isEmpty()) {
			any.add("EXISTS (SELECT 1 FROM issue_mention m WHERE m.tenant_id = ? AND m.issue_id = i.id"
					+ " AND m.mentioned_faculty_id IN (" + placeholders(visibility.mentionedFaculty().size()) + "))");
			parameters.add(tenant());
			parameters.addAll(visibility.mentionedFaculty());
		}
		return any.isEmpty() ? " AND FALSE" : " AND (" + String.join(" OR ", any) + ")";
	}

	private void insertMention(UUID issueId, UUID facultyId) {
		Instant now = now();
		execute(INSERT_MENTION, newId(), tenantForWrite(), now, actor(), now, actor(), issueId, facultyId);
	}

	/** The second column of each row, grouped by the issue in the first, for a page of issues in one query. */
	private Map<UUID, Set<UUID>> pairsOf(String sql, String valueColumn, Collection<UUID> issueIds) {
		if (issueIds.isEmpty()) {
			return Map.of();
		}
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		parameters.addAll(issueIds);
		Map<UUID, Set<UUID>> grouped = new HashMap<>();
		queryList(sql.formatted(placeholders(issueIds.size())), (row) -> {
			grouped.computeIfAbsent(uuid(row, "issue_id"), (issue) -> new HashSet<>()).add(uuid(row, valueColumn));
			return Boolean.TRUE;
		}, parameters.toArray());
		return grouped;
	}

	private static Issue toIssue(ResultSet row) throws SQLException {
		return new Issue(uuid(row, "id"), uuid(row, "tenant_id"), SessionType.valueOf(row.getString("session_type")),
				IssueStatus.valueOf(row.getString("status")), uuid(row, "department_id"), uuid(row, "division_id"),
				uuidOrNull(row, "batch_id"), row.getString("title"), row.getString("description"),
				uuid(row, "opener_user_id"), instantOrNull(row, "closed_at"), uuidOrNull(row, "closed_by_user_id"),
				row.getLong("version"), instant(row, "created_at"), instant(row, "updated_at"));
	}

}
