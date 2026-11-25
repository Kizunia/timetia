// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ReviewDialog } from "@/features/tickets/components/ReviewDialog";
import { SubmitDialog } from "@/features/tickets/components/SubmitDialog";
import { TicketDetailScreen } from "@/features/tickets/TicketDetailScreen";
import { TicketsScreen } from "@/features/tickets/TicketsScreen";

import { HOD, OTHER, pageOf, review, ticket } from "./tickets-fixtures";
import { json, meWith, problem, renderScreen, stubApi, type Call } from "./ui";

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const lists = (calls: Call[]) => calls.filter((call) => call.path === "/api/v1/tickets");

describe("the ticket list", () => {
  it("asks for what each tab means and links a row to its ticket", async () => {
    const stub = stubApi(() => json(pageOf([ticket()])));
    renderScreen(<TicketsScreen />, meWith(["rescheduling.tickets.view", "rescheduling.tickets.create"]));

    expect(await screen.findByRole("link", { name: "Reschedule" })).toHaveAttribute("href", "/tickets/t-1");
    expect(lists(stub.calls)[0]!.query.get("mine")).toBe("true");

    await userEvent.click(screen.getByRole("tab", { name: "To review" }));
    await waitFor(() => expect(lists(stub.calls).some((call) => call.query.get("awaitingReview") === "true")).toBe(true));

    await userEvent.click(screen.getByRole("tab", { name: "All I can see" }));
    await waitFor(() =>
      expect(lists(stub.calls).some((call) => !call.query.has("mine") && !call.query.has("awaitingReview"))).toBe(true),
    );
  });

  it("narrows by status, and says what an empty tab means", async () => {
    const stub = stubApi(() => json(pageOf([])));
    renderScreen(<TicketsScreen />, meWith(["rescheduling.tickets.view", "rescheduling.tickets.create"]));
    await userEvent.click(await screen.findByRole("tab", { name: "Mine" }));
    expect(await screen.findByText("You have not raised any tickets")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("combobox", { name: "Status" }));
    await userEvent.click(screen.getByRole("option", { name: "Applied" }));

    await waitFor(() => expect(lists(stub.calls).some((call) => call.query.get("status") === "APPLIED")).toBe(true));
  });

  it("says why the list could not be loaded", async () => {
    stubApi(() => problem(500, "internal_error"));
    renderScreen(<TicketsScreen />, meWith(["rescheduling.tickets.view"]));

    expect(await screen.findByText("The tickets could not be loaded")).toBeInTheDocument();
  });
});

describe("one ticket", () => {
  const detail = (body: unknown, apply?: () => Response) =>
    stubApi((call) => (call.method === "POST" && apply ? apply() : json(body)));

  it("offers the author of a draft Apply, Send for review, Edit and Withdraw", async () => {
    detail(ticket());
    renderScreen(<TicketDetailScreen id="t-1" />, meWith([]));

    for (const name of ["Apply", "Send for review", "Edit", "Withdraw"]) {
      expect(await screen.findByRole("button", { name })).toBeInTheDocument();
    }
  });

  it("offers an HOD who raised it for a teacher no Apply", async () => {
    detail(ticket({ actingFacultyId: "f-1" }));
    renderScreen(<TicketDetailScreen id="t-1" />, meWith([]));

    expect(await screen.findByRole("button", { name: "Send for review" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Apply" })).not.toBeInTheDocument();
  });

  it("offers a reviewer who has not answered only Review", async () => {
    detail(ticket({ authorUserId: OTHER, status: "IN_REVIEW", reviews: [review({ reviewerUserId: meWith([]).id })] }));
    renderScreen(<TicketDetailScreen id="t-1" />, meWith([]));

    expect(await screen.findByRole("button", { name: "Review" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Apply" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Withdraw" })).not.toBeInTheDocument();
  });

  it("offers someone who only sees it nothing", async () => {
    detail(ticket({ authorUserId: OTHER }));
    renderScreen(<TicketDetailScreen id="t-1" />, meWith(["rescheduling.tickets.view-in-scope"]));

    expect(await screen.findByText("There is nothing for you to do on this ticket.")).toBeInTheDocument();
  });

  it("shows what stood in the way when applying is blocked", async () => {
    detail(ticket(), () =>
      problem(409, "rescheduling.blocked_by_conflicts", {
        errors: [{ field: "target", code: "room_occupied", message: "R102 is taken on Thursday at 09:00" }],
      }),
    );
    renderScreen(<TicketDetailScreen id="t-1" />, meWith([]));

    await userEvent.click(await screen.findByRole("button", { name: "Apply" }));

    expect(await screen.findByText(/R102 is taken on Thursday at 09:00/)).toBeInTheDocument();
    expect(screen.getByText("Room is taken")).toBeInTheDocument();
  });

  it("says a ticket that may not be seen could not be found", async () => {
    stubApi(() => problem(404, "rescheduling.ticket_not_found"));
    renderScreen(<TicketDetailScreen id="t-9" />, meWith([]));

    expect(await screen.findByText(/could not be found/)).toBeInTheDocument();
  });
});

describe("sending a ticket for review", () => {
  const candidates = [
    { userId: HOD, name: "Dr HOD", capacity: "HOD" },
    { userId: OTHER, name: "Dr Roy", capacity: "OTHER_FACULTY" },
  ];

  it("needs at least one person, and for a permanent change an HOD", async () => {
    const stub = stubApi((call) => (call.method === "GET" ? json(candidates) : json(ticket({ status: "IN_REVIEW" }))));
    renderScreen(<SubmitDialog ticket={ticket({ type: "PERMANENT_CHANGE" })} open onOpenChange={() => {}} />);
    await screen.findByRole("checkbox", { name: "Dr Roy" });
    const send = screen.getByRole("button", { name: "Send for review" });

    await userEvent.click(send);
    expect(screen.getByRole("alert")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("checkbox", { name: "Dr Roy" }));
    await userEvent.click(send);
    expect(screen.getByText("A permanent change needs an HOD. Choose one.")).toBeInTheDocument();
    expect(stub.calls.some((call) => call.method === "POST")).toBe(false);

    await userEvent.click(screen.getByRole("checkbox", { name: "Dr HOD" }));
    await userEvent.click(send);
    await waitFor(() =>
      expect(stub.calls.find((call) => call.method === "POST")?.body).toEqual({
        reviewers: [
          { userId: HOD, capacity: "HOD" },
          { userId: OTHER, capacity: "OTHER_FACULTY" },
        ],
      }),
    );
  });
});

describe("reviewing", () => {
  it("needs a reason to reject", async () => {
    const stub = stubApi(() => json(ticket({ status: "REJECTED" })));
    renderScreen(<ReviewDialog ticket={ticket()} decisions={["APPROVE", "REJECT"]} open onOpenChange={() => {}} />);

    await userEvent.click(screen.getByRole("radio", { name: "Reject" }));
    await userEvent.click(screen.getByRole("button", { name: "Send answer" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(stub.calls).toHaveLength(0);

    await userEvent.type(screen.getByRole("textbox", { name: "Why do you reject it?" }), "Clashes with a test");
    await userEvent.click(screen.getByRole("button", { name: "Send answer" }));
    await waitFor(() => expect(stub.calls[0]?.body).toEqual({ decision: "REJECT", reason: "Clashes with a test" }));
  });

  it("offers no Acknowledge to the HOD of a permanent change", () => {
    renderScreen(<ReviewDialog ticket={ticket()} decisions={["APPROVE", "REJECT"]} open onOpenChange={() => {}} />);

    expect(screen.queryByRole("radio", { name: /Acknowledge/ })).not.toBeInTheDocument();
  });
});
