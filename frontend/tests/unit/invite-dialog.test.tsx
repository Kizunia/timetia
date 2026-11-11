// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { InviteDialog } from "@/features/identity/InviteDialog";

import { json, meWith, problem, renderScreen, stubApi } from "./ui";

const { success } = vi.hoisted(() => ({ success: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));

const invited = {
  id: "33333333-3333-7333-8333-333333333333",
  email: "new.person@kibootia.test",
  username: "new.person@kibootia.test",
  name: "Unnamed user",
  state: "INVITED",
  emailVerified: true,
  erasureBegun: false,
  roles: [],
  version: 0,
  createdAt: "2026-11-10T10:00:00Z",
  updatedAt: "2026-11-10T10:00:00Z",
};

async function openAndFill(email: string) {
  await userEvent.click(screen.getByRole("button", { name: "Invite user" }));
  const field = await screen.findByLabelText("Email address");
  if (email) await userEvent.type(field, email);
  await userEvent.click(screen.getByRole("button", { name: "Send invitation" }));
}

describe("inviting a user", () => {
  beforeEach(() => success.mockClear());

  it("asks for an address before anything is sent", async () => {
    const api = stubApi(() => json({}, 500));
    renderScreen(<InviteDialog />);

    await openAndFill("");

    expect(await screen.findByText("Enter an email address")).toBeInTheDocument();
    expect(api.calls).toHaveLength(0);
  });

  it("refuses something that is not an address before anything is sent", async () => {
    const api = stubApi(() => json({}, 500));
    renderScreen(<InviteDialog />);

    await openAndFill("not-an-address");

    expect(await screen.findByText("Enter a valid email address")).toBeInTheDocument();
    expect(api.calls).toHaveLength(0);
  });

  it("sends the invitation, tidies the address, says so and closes", async () => {
    const api = stubApi(() => json(invited, 201));
    renderScreen(<InviteDialog />);

    await openAndFill("  new.person@kibootia.test ");

    await waitFor(() => expect(success).toHaveBeenCalledWith("Invitation sent to new.person@kibootia.test"));
    expect(api.calls[0]).toMatchObject({ method: "POST", path: "/api/v1/users", body: { email: "new.person@kibootia.test", roleIds: [] } });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("shows the API's refusal on the address field when someone already has that address", async () => {
    stubApi(() => problem(409, "identity.email_in_use"));
    renderScreen(<InviteDialog />);

    await openAndFill("taken@kibootia.test");

    expect(await screen.findByText("Someone with this email address already has an account.")).toBeInTheDocument();
    expect(screen.getByLabelText("Email address")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(success).not.toHaveBeenCalled();
  });

  it("puts the server's own validation message on the field it names, even when the form's checks passed", async () => {
    stubApi(() =>
      problem(400, "platform.validation_failed", {
        errors: [{ field: "email", code: "invalid_format", message: "must be a valid email address" }],
      }),
    );
    renderScreen(<InviteDialog />);

    await openAndFill("looks@fine.test");

    expect(await screen.findByText("must be a valid email address")).toBeInTheDocument();
  });

  it("explains a failure that belongs to no field, with the reference to quote", async () => {
    stubApi(() => problem(503, "platform.service_unavailable", { traceId: "abc123" }));
    renderScreen(<InviteDialog />);

    await openAndFill("later@kibootia.test");

    expect(await screen.findByText(/not available right now/)).toBeInTheDocument();
    expect(screen.getByText("Reference: abc123")).toBeInTheDocument();
  });

  it("offers roles to give only to someone who may view and assign them", async () => {
    stubApi((call) =>
      call.path === "/api/v1/roles"
        ? json({
            items: [
              { id: "r-user", name: "User", builtIn: "USER", description: "", permissions: [], assignedUsers: 3, version: 0, createdAt: "", updatedAt: "" },
              { id: "r-editor", name: "Editors", builtIn: null, description: "", permissions: [], assignedUsers: 0, version: 0, createdAt: "", updatedAt: "" },
            ],
            page: { number: 0, size: 100, totalElements: 2, totalPages: 1 },
          })
        : json(invited, 201),
    );
    renderScreen(<InviteDialog />, meWith(["identity.users.invite", "access.roles.view", "access.roles.assign"]));

    await userEvent.click(screen.getByRole("button", { name: "Invite user" }));

    expect(await screen.findByLabelText("Editors")).toBeInTheDocument();
    expect(screen.queryByLabelText("User")).not.toBeInTheDocument();
  });
});
