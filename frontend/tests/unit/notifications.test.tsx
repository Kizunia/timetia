// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { NotificationBell } from "@/features/notifications/components/NotificationBell";
import { describeNotification, type Described } from "@/features/notifications/lib/describe";
import { NotificationsScreen } from "@/features/notifications/NotificationsScreen";

import { json, meWith, renderScreen, stubApi } from "./ui";

function about(overrides: Partial<Described> = {}): Described {
  return {
    type: "TICKET_APPLIED",
    ticketId: "t-1",
    issueId: null,
    lectureId: "l-1",
    actorName: "Dr Rao",
    ticketType: "RESCHEDULE",
    ticketVisible: true,
    ...overrides,
  };
}

describe("what a notification says", () => {
  it.each([
    ["REVIEW_REQUESTED", "Dr Rao asked you to review a reschedule"],
    ["REVIEW_APPROVED", "Dr Rao approved your reschedule"],
    ["REVIEW_REJECTED", "Dr Rao rejected your reschedule"],
    ["TICKET_APPLIED", "A reschedule was applied"],
    ["TICKET_REVERTED", "A reschedule was undone"],
    ["TICKET_CONFLICTED", "Something is now in the way of a reschedule"],
    ["TICKET_EDITED", "Dr Rao changed a reschedule you were asked to review. Please look again"],
    ["TICKET_WITHDRAWN", "Dr Rao withdrew a reschedule you were asked to review"],
  ] as const)("%s", (type, text) => {
    expect(describeNotification(about({ type }))).toEqual({ text, href: "/tickets/t-1" });
  });

  it("names nobody it does not know and calls an unknown ticket a ticket", () => {
    expect(describeNotification(about({ type: "REVIEW_APPROVED", actorName: null, ticketType: null })).text).toBe(
      "Someone approved your ticket",
    );
  });

  it("sends someone who may not open the ticket to the lecture, or nowhere without one", () => {
    expect(describeNotification(about({ ticketVisible: false })).href).toBe("/timetable?lecture=l-1");
    expect(describeNotification(about({ ticketVisible: false, lectureId: null })).href).toBeNull();
  });

  it("links a mention to the issue", () => {
    const mention = about({ type: "ISSUE_MENTION", ticketId: null, issueId: "i-1", lectureId: null, ticketType: null, ticketVisible: false });
    expect(describeNotification(mention)).toEqual({ text: "Dr Rao mentioned you on an issue", href: "/issues/i-1" });
  });
});

function notification(id: string, overrides: Record<string, unknown> = {}) {
  return { id, actorUserId: "u-1", createdAt: "2026-11-02T10:00:00Z", readAt: null, ...about(), ...overrides };
}

function api(count: number, items: unknown[] = [notification("n-1")]) {
  return stubApi((call) => {
    if (call.path === "/api/v1/notifications/unread-count") return json({ count });
    if (call.method === "POST") return json(call.path.endsWith("read-all") ? { marked: count } : notification("n-1"));
    return json({ items, page: { number: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 } });
  });
}

describe("the bell", () => {
  it("shows how many are unread, at most 99+", async () => {
    api(150);
    renderScreen(<NotificationBell />);

    expect(await screen.findByRole("link", { name: "Notifications, 150 unread" })).toHaveTextContent("99+");
  });

  it("shows no number when everything is read", async () => {
    const stub = api(0);
    renderScreen(<NotificationBell />);

    await waitFor(() => expect(stub.calls.length).toBeGreaterThan(0));
    expect(screen.getByRole("link", { name: "Notifications" })).toBeInTheDocument();
    expect(screen.queryByTestId("unread-count")).not.toBeInTheDocument();
  });
});

describe("the notifications screen", () => {
  it("links the ticket only when the server says the person may open it, whatever permissions they hold", async () => {
    api(1, [notification("n-1", { ticketVisible: false })]);
    const { unmount } = renderScreen(<NotificationsScreen />, meWith(["rescheduling.tickets.view"]));
    expect(await screen.findByRole("link", { name: "A reschedule was applied" })).toHaveAttribute("href", "/timetable?lecture=l-1");
    unmount();

    api(1);
    renderScreen(<NotificationsScreen />, meWith([]));
    expect(await screen.findByRole("link", { name: "A reschedule was applied" })).toHaveAttribute("href", "/tickets/t-1");
  });

  it("asks for the unread ones on the Unread tab", async () => {
    const stub = api(1);
    renderScreen(<NotificationsScreen />);
    await screen.findByRole("link", { name: "A reschedule was applied" });

    await userEvent.click(screen.getByRole("tab", { name: "Unread" }));

    await waitFor(() => expect(stub.calls.some((call) => call.query.get("unread") === "true")).toBe(true));
  });

  it("marks one read, and all of them", async () => {
    const stub = api(2, [notification("n-1"), notification("n-2", { readAt: "2026-11-02T11:00:00Z" })]);
    renderScreen(<NotificationsScreen />);

    await userEvent.click(await screen.findByRole("button", { name: "Mark read" }));
    await waitFor(() => expect(stub.calls.some((call) => call.method === "POST" && call.path === "/api/v1/notifications/n-1/read")).toBe(true));

    await userEvent.click(screen.getByRole("button", { name: "Mark all read" }));
    await waitFor(() => expect(stub.calls.some((call) => call.path === "/api/v1/notifications/read-all")).toBe(true));
  });

  it("offers nothing to mark when everything is read", async () => {
    api(0, []);
    renderScreen(<NotificationsScreen />);

    expect((await screen.findAllByText("Nothing to show")).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Mark all read" })).toBeDisabled();
  });
});
