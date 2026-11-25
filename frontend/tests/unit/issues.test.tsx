// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { IssueDetailScreen } from "@/features/issues/IssueDetailScreen";
import { issueActions } from "@/features/issues/lib/actions";
import { issueBody, issueSchema, type IssueValues } from "@/features/issues/schemas";

import { json, meWith, problem, renderScreen, stubApi } from "./ui";

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const ME = "11111111-1111-7111-8111-111111111111";
const OTHER = "22222222-2222-7222-8222-222222222222";

function values(overrides: Partial<IssueValues> = {}): IssueValues {
  return { sessionType: "THEORY", divisionId: "d-1", batchId: "b-1", title: " Extra class ", description: "", mentionedFacultyIds: ["f-1"], ...overrides };
}

describe("the open-issue form's checks", () => {
  const messages = (input: IssueValues) => issueSchema.safeParse(input).error?.issues.map((issue) => issue.message) ?? [];

  it("wants a division for theory and a batch for a lab", () => {
    expect(messages(values({ divisionId: undefined }))).toEqual(["Choose a division"]);
    expect(messages(values({ sessionType: "LAB", batchId: undefined }))).toEqual(["Choose a batch"]);
  });

  it("limits the title, the description and the mentions", () => {
    expect(messages(values({ title: " " }))).toEqual(["Enter a title"]);
    expect(messages(values({ description: "x".repeat(2001) }))).toEqual(["Use at most 2000 characters"]);
    expect(messages(values({ mentionedFacultyIds: Array.from({ length: 11 }, (_, i) => `f-${i}`) }))).toEqual(["Mention at most 10 teachers"]);
  });

  it("sends a division for theory and a batch for a lab, never both", () => {
    expect(issueBody(values())).toEqual({ sessionType: "THEORY", divisionId: "d-1", title: "Extra class", mentionedFacultyIds: ["f-1"] });
    expect(issueBody(values({ sessionType: "LAB", description: " why " }))).toEqual({
      sessionType: "LAB",
      batchId: "b-1",
      title: "Extra class",
      description: "why",
      mentionedFacultyIds: ["f-1"],
    });
  });
});

describe("what a person may do with an issue", () => {
  const issue = { status: "OPEN", openerUserId: ME, mentionedFacultyIds: ["f-1"] } as const;
  const of = (userId: string, faculty: string[], status: "OPEN" | "CLOSED" = "OPEN") =>
    issueActions({ ...issue, mentionedFacultyIds: [...issue.mentionedFacultyIds], status }, userId, faculty);

  it("lets the opener close, link and mention", () => {
    expect(of(ME, [])).toEqual({ close: true, link: true, mention: true });
  });

  it("lets a mentioned teacher close and link, and nobody else do anything", () => {
    expect(of(OTHER, ["f-1"])).toEqual({ close: true, link: true, mention: false });
    expect(of(OTHER, ["f-2"])).toEqual({ close: false, link: false, mention: false });
  });

  it("offers nothing on a closed issue", () => {
    expect(of(ME, ["f-1"], "CLOSED")).toEqual({ close: false, link: false, mention: false });
  });
});

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: "i-1",
    status: "OPEN",
    sessionType: "THEORY",
    departmentId: "dep-1",
    divisionId: "d-1",
    batchId: null,
    title: "Extra class",
    description: null,
    openerUserId: ME,
    openerName: "Alice Example",
    divisionCode: "CSE-A",
    batchCode: null,
    mentionedFacultyIds: ["f-1"],
    mentionedFaculty: [{ id: "f-1", name: "Dr Rao" }],
    linkedTicketIds: ["ticket-abc123"],
    closedAt: null,
    closedByUserId: null,
    closedByName: null,
    version: 0,
    createdAt: "2026-11-02T10:00:00Z",
    updatedAt: "2026-11-02T10:00:00Z",
    ...overrides,
  };
}

function api(issue: Record<string, unknown>, close: () => Response = () => json(issue)) {
  return stubApi((call) => {
    if (call.path === "/api/v1/directory/me") return json({ faculty: [], assignments: [] });
    if (call.path === "/api/v1/me/scoped-roles") return json([]);
    if (call.method === "POST" && call.path.endsWith("/close")) return close();
    return json(issue);
  });
}

describe("one issue", () => {
  it("shows names, not ids, and offers the opener every button", async () => {
    api(detail());
    renderScreen(<IssueDetailScreen id="i-1" />, meWith(["rescheduling.tickets.view"]));

    expect(await screen.findByRole("heading", { name: "Extra class" })).toBeInTheDocument();
    expect(screen.getByText(/Theory · CSE-A · opened by Alice Example/)).toBeInTheDocument();
    expect(screen.getByText("Concerns: Dr Rao")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "ticket abc123" })).toHaveAttribute("href", "/tickets/ticket-abc123");
    for (const name of ["Mention a teacher", "Link a ticket", "Close the issue"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
  });

  it("offers someone who is neither opener nor mentioned nothing, and no ticket link they cannot follow", async () => {
    api(detail({ openerUserId: OTHER }));
    renderScreen(<IssueDetailScreen id="i-1" />, meWith([]));

    await screen.findByRole("heading", { name: "Extra class" });
    expect(screen.queryByRole("button", { name: "Close the issue" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /ticket abc123/ })).not.toBeInTheDocument();
    expect(screen.getByText("ticket abc123")).toBeInTheDocument();
  });

  it("says who closed it", async () => {
    api(detail({ status: "CLOSED", closedAt: "2026-11-03T10:00:00Z", closedByName: "Dr Rao" }));
    renderScreen(<IssueDetailScreen id="i-1" />);

    expect(await screen.findByText(/Closed by Dr Rao on/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Close the issue" })).not.toBeInTheDocument();
  });

  it("closes only after a confirmation, and says when the issue was already closed", async () => {
    const stub = api(detail(), () => problem(409, "issues.issue_not_open"));
    renderScreen(<IssueDetailScreen id="i-1" />);

    await userEvent.click(await screen.findByRole("button", { name: "Close the issue" }));
    expect(stub.calls.some((call) => call.path.endsWith("/close"))).toBe(false);
    await userEvent.click(screen.getByRole("button", { name: "Close issue" }));

    await waitFor(() => expect(screen.getByText("The issue is closed.")).toBeInTheDocument());
  });
});
