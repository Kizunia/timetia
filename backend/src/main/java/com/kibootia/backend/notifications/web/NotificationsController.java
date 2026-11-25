package com.kibootia.backend.notifications.web;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.notifications.application.Notification;
import com.kibootia.backend.notifications.application.NotificationService;
import com.kibootia.backend.notifications.application.NotificationView;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/** The signed-in person's own notifications: to read, count and mark. Nobody can see another person's. */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationsController {

	private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt");

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final NotificationService notifications;

	NotificationsController(NotificationService notifications) {
		this.notifications = notifications;
	}

	/** @param unread only the notifications not yet read */
	@GetMapping
	PageResponse<NotificationResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(defaultValue = "false") boolean unread) {
		return PageResponse.of(this.notifications.list(unread, query.toPageable(SORTABLE, NEWEST_FIRST)),
				NotificationsController::toResponse);
	}

	@GetMapping("/unread-count")
	UnreadCountResponse unreadCount() {
		return new UnreadCountResponse(this.notifications.unreadCount());
	}

	@PostMapping("/{id}/read")
	NotificationResponse read(@PathVariable UUID id) {
		return toResponse(this.notifications.markRead(id));
	}

	@PostMapping("/read-all")
	MarkedResponse readAll() {
		return new MarkedResponse(this.notifications.markAllRead());
	}

	private static NotificationResponse toResponse(NotificationView view) {
		Notification notification = view.notification();
		return new NotificationResponse(notification.id(), notification.type(), notification.ticketId(), notification.issueId(),
				notification.actorUserId(), notification.createdAt(), notification.readAt(), view.actorName(), view.ticketType(),
				view.lectureId(), view.ticketVisible());
	}

}
