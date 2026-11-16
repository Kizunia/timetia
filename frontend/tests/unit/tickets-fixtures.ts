import type { Ticket } from "@/features/tickets/api";

/** The id `meWith` gives the signed-in user, so a ticket can be written by "me" or by someone else. */
export const ME = "11111111-1111-7111-8111-111111111111";
export const OTHER = "22222222-2222-7222-8222-222222222222";
export const HOD = "33333333-3333-7333-8333-333333333333";

export function change(overrides: Record<string, unknown> = {}) {
  return {
    operation: "MOVE",
    lectureId: "l-1",
    assignmentId: null,
    target: { date: "2026-07-09", startTime: "09:00:00", endTime: "10:00:00", mode: "IN_PERSON", roomId: "r-2", roomCode: "R102", meetingLink: null },
    reason: null,
    substituteFacultyId: null,
    substituteFacultyName: null,
    before: null,
    beforeFacultyId: null,
    beforeFacultyName: null,
    lecture: { date: "2026-07-06", startTime: "09:00:00", endTime: "10:00:00", mode: "IN_PERSON", roomCode: "R101", subjectCode: "OOP", facultyName: "Dr Rao" },
    ...overrides,
  };
}

export function review(overrides: Record<string, unknown> = {}) {
  return {
    reviewerUserId: OTHER,
    reviewerName: "Dr Roy",
    capacity: "OTHER_FACULTY",
    status: "PENDING",
    reason: null,
    decidedAt: null,
    revisionNo: 1,
    escalation: false,
    ...overrides,
  };
}

/** A ticket as the detail endpoint returns it: by default a draft reschedule written by the signed-in user. */
export function ticket(overrides: Record<string, unknown> = {}): Ticket {
  const first = change();
  return {
    id: "t-1",
    type: "RESCHEDULE",
    status: "DRAFT",
    approvalPath: null,
    authorUserId: ME,
    authorName: "Alice Example",
    actingFacultyId: null,
    description: null,
    revisionNo: 1,
    divisionId: "d-1",
    batchId: null,
    sessionType: "THEORY",
    facultyId: "f-1",
    subjectCode: "OOP",
    subjectName: "Object-Oriented Programming",
    divisionCode: "CSE-A",
    batchCode: null,
    facultyName: "Dr Rao",
    change: first,
    changes: [first],
    conflicts: [],
    conflicted: false,
    reviews: [],
    expiresAt: "2026-07-06T03:30:00Z",
    closedAt: null,
    revertsTicketId: null,
    revertedByTicketId: null,
    version: 0,
    createdAt: "2026-07-01T10:00:00Z",
    updatedAt: "2026-07-01T10:00:00Z",
    ...overrides,
  } as unknown as Ticket;
}

/** A page of the paged API's answer. */
export function pageOf(items: unknown[], number = 0, totalPages = 1) {
  return { items, page: { number, size: 20, totalElements: items.length, totalPages } };
}
