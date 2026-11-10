// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AuditScreen, PersonLabel } from "@/features/audit/AuditScreen";

import { json, renderScreen, stubApi } from "./ui";

describe("people in the audit log (ADR 0046)", () => {
  it("shows a user's current name, the system as the system, and an erased user as a deleted user", () => {
    render(
      <>
        <PersonLabel person={{ id: "u1", kind: "USER", name: "Alice Example" }} />
        <PersonLabel person={{ id: "00000000-0000-0000-0000-000000000000", kind: "SYSTEM" }} />
        <PersonLabel person={{ id: "u3", kind: "ERASED", name: null }} />
      </>,
    );

    expect(screen.getByText("Alice Example")).toBeInTheDocument();
    expect(screen.getByText("System")).toBeInTheDocument();
    expect(screen.getByText("Deleted user")).toHaveAttribute("title", "u3");
  });

  it("lists the entries newest first, with who did what to whom and what changed", async () => {
    stubApi(() =>
      json({
        items: [
          {
            id: "e1",
            occurredAt: "2026-11-10T10:00:00Z",
            actor: { id: "a", kind: "USER", name: "Admin Person" },
            action: "access.role.assigned",
            targetType: "user",
            targetId: "t",
            target: { id: "t", kind: "ERASED", name: null },
            changes: { role: "Reviewers" },
            outcome: "success",
          },
          {
            id: "e2",
            occurredAt: "2026-11-10T09:00:00Z",
            actor: { id: "s", kind: "SYSTEM" },
            action: "identity.user.deleted",
            targetType: "user",
            targetId: "1234567890",
            changes: {},
            outcome: "forbidden",
          },
        ],
        page: { number: 0, size: 25, totalElements: 2, totalPages: 1 },
      }),
    );
    renderScreen(<AuditScreen />);

    const rows = await screen.findAllByTestId("audit-row");

    expect(rows).toHaveLength(2);
    expect(within(rows[0]!).getByText("Admin Person")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("access.role.assigned")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("Deleted user")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("role: Reviewers")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("System")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("user 12345678")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("forbidden")).toBeInTheDocument();
  });

  it("says why the log could not be shown", async () => {
    stubApi(() => json({ type: "about:blank", title: "Forbidden", status: 403, code: "platform.forbidden" }, 403));
    renderScreen(<AuditScreen />);

    expect(await screen.findByRole("alert")).toHaveTextContent("You are not allowed to do that.");
  });
});
