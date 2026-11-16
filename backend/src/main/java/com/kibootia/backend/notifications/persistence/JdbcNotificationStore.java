package com.kibootia.backend.notifications.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.notifications.application.NewNotification;
import com.kibootia.backend.notifications.application.Notification;
import com.kibootia.backend.notifications.application.NotificationStore;
import com.kibootia.backend.notifications.domain.NotificationType;
import com.kibootia.backend.platform.persistence.JdbcSupport;

/**
 * Keeps notifications in MySQL with hand-written SQL over JDBC. Only the time a notification was read ever changes.
 */
@Component
class JdbcNotificationStore extends JdbcSupport implements NotificationStore {

	/** The columns the sort order of a page may use, by the property name the web layer sorts on. */
	private static final Map<String, String> SORT_COLUMNS = Map.of("createdAt", "created_at");

	private static final String COLUMNS = """
			id, recipient_user_id, notification_type, ticket_id, issue_id, actor_user_id, created_at, read_at
			""";

	// Was this person already told of this event in this way?
	private static final String COUNT_SAME_EVENT = """
			SELECT COUNT(*) FROM notification
			WHERE tenant_id = ? AND source_event_id = ? AND recipient_user_id = ? AND notification_type = ?
			""";

	private static final String INSERT = """
			INSERT INTO notification (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			                          recipient_user_id, notification_type, ticket_id, issue_id, actor_user_id, source_event_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	// One person's notifications; every one, or only the unread ones when the first flag is true.
	private static final String SEARCH = "SELECT " + COLUMNS + """
			FROM notification
			WHERE tenant_id = ? AND recipient_user_id = ? AND (? = FALSE OR read_at IS NULL)""";

	private static final String COUNT_SEARCH = """
			SELECT COUNT(*) FROM notification
			WHERE tenant_id = ? AND recipient_user_id = ? AND (? = FALSE OR read_at IS NULL)
			""";

	// How many notifications has this person not read yet?
	private static final String COUNT_UNREAD = """
			SELECT COUNT(*) FROM notification
			WHERE tenant_id = ? AND recipient_user_id = ? AND read_at IS NULL
			""";

	// One notification, if it is this person's.
	private static final String FIND_OF = "SELECT " + COLUMNS + """
			FROM notification
			WHERE tenant_id = ? AND recipient_user_id = ? AND id = ?
			""";

	// One notification with the version a change is checked against.
	private static final String FIND_WITH_VERSION = "SELECT " + COLUMNS + """
			, version FROM notification
			WHERE tenant_id = ? AND id = ?
			""";

	private static final String MARK_READ = """
			UPDATE notification
			SET read_at = ?, version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	// Every unread notification of one person. Only the time read changes, as it did when this was a bulk JPQL update.
	private static final String MARK_ALL_READ = """
			UPDATE notification SET read_at = ?
			WHERE tenant_id = ? AND recipient_user_id = ? AND read_at IS NULL
			""";

	JdbcNotificationStore(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void insertAll(List<NewNotification> toWrite) {
		for (NewNotification notification : toWrite) {
			// A person is told once of an event, even if the event is delivered again.
			long alreadyTold = queryCount(COUNT_SAME_EVENT, tenant(), notification.sourceEventId(),
					notification.recipientUserId(), notification.type());
			if (alreadyTold == 0) {
				insert(notification);
			}
		}
	}

	private void insert(NewNotification notification) {
		Instant now = now();
		execute(INSERT, newId(), tenantForWrite(), now, actor(), now, actor(), notification.recipientUserId(),
				notification.type(), notification.ticketId(), notification.issueId(), notification.actorUserId(),
				notification.sourceEventId());
	}

	@Override
	public Page<Notification> search(UUID recipient, boolean unreadOnly, Pageable pageable) {
		return queryPage(SEARCH + orderBy(pageable.getSort(), SORT_COLUMNS), COUNT_SEARCH,
				JdbcNotificationStore::toNotification, pageable, tenant(), recipient, unreadOnly);
	}

	@Override
	public long countUnread(UUID recipient) {
		return queryCount(COUNT_UNREAD, tenant(), recipient);
	}

	@Override
	public Optional<Notification> findOf(UUID recipient, UUID id) {
		return queryOne(FIND_OF, JdbcNotificationStore::toNotification, tenant(), recipient, id);
	}

	@Override
	public Notification markRead(UUID id, Instant at) {
		VersionedNotification current = queryOne(FIND_WITH_VERSION,
				(row) -> new VersionedNotification(toNotification(row), row.getLong("version")), tenant(), id)
			.orElseThrow();
		Notification notification = current.notification();
		if (notification.read()) {
			return notification;
		}
		int changed = execute(MARK_READ, at, now(), actor(), tenant(), id, current.version());
		requireOneRow(changed, "Notification", id);
		return new Notification(notification.id(), notification.recipientUserId(), notification.type(),
				notification.ticketId(), notification.issueId(), notification.actorUserId(), notification.createdAt(), at);
	}

	@Override
	public int markAllRead(UUID recipient, Instant at) {
		return execute(MARK_ALL_READ, at, tenant(), recipient);
	}

	private static Notification toNotification(ResultSet row) throws SQLException {
		return new Notification(uuid(row, "id"), uuid(row, "recipient_user_id"),
				NotificationType.valueOf(row.getString("notification_type")), uuidOrNull(row, "ticket_id"),
				uuidOrNull(row, "issue_id"), uuidOrNull(row, "actor_user_id"), instant(row, "created_at"),
				instantOrNull(row, "read_at"));
	}

	private record VersionedNotification(Notification notification, long version) {

	}

}
