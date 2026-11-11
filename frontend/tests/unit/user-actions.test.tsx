// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { User } from "@/features/identity/api";
import { availableActions, UserActions } from "@/features/identity/UserActions";

const SELF = "11111111-1111-7111-8111-111111111111";
const OTHER = "22222222-2222-7222-8222-222222222222";

function user(overrides: Partial<User> = {}): User {
  return {
    id: OTHER,
    username: "bob",
    email: "bob@kibootia.test",
    name: "Bob Example",
    state: "ACTIVE",
    emailVerified: true,
    erasureBegun: false,
    roles: [],
    version: 0,
    createdAt: "2026-11-01T10:00:00Z",
    updatedAt: "2026-11-01T10:00:00Z",
    ...overrides,
  };
}

const everything = () => true;
const nothing = () => false;
const names = (u: User, can: (permission: string) => boolean = everything, self = SELF) => availableActions(u, self, can).map((spec) => spec.action);

describe("which actions an administrator is offered on a user", () => {
  it("offers disable and delete for an active user, and nothing that does not apply", () => {
    expect(names(user({ state: "ACTIVE" }))).toEqual(["disable", "delete"]);
  });

  it("offers enable and delete for a disabled user", () => {
    expect(names(user({ state: "DISABLED" }))).toEqual(["enable", "delete"]);
  });

  it("offers the invitation again, disable and delete for a user who has not accepted", () => {
    expect(names(user({ state: "INVITED" }))).toEqual(["resend-invitation", "disable", "delete"]);
  });

  it("offers restore and erase for a deleted user, and nothing once erasing has begun", () => {
    expect(names(user({ state: "DELETED" }))).toEqual(["restore", "erase"]);
    expect(names(user({ state: "DELETED", erasureBegun: true }))).toEqual([]);
  });

  it("offers only what the administrator may do", () => {
    const onlyDisable = (permission: string) => permission === "identity.users.disable";

    expect(names(user({ state: "ACTIVE" }), onlyDisable)).toEqual(["disable"]);
    expect(names(user({ state: "ACTIVE" }), nothing)).toEqual([]);
  });

  it("never offers disable or delete on the administrator's own account", () => {
    expect(names(user({ id: SELF, state: "ACTIVE" }))).toEqual([]);
  });
});

describe("the actions on screen", () => {
  it("asks for confirmation, says what it will do, and acts only when confirmed", async () => {
    const onAction = vi.fn();
    render(<UserActions user={user()} selfId={SELF} can={everything} busy={false} onAction={onAction} />);

    await userEvent.click(screen.getByRole("button", { name: "Disable" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).getByText("Disable this user?")).toBeInTheDocument();
    expect(onAction).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(onAction).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "Disable" }));
    await userEvent.click(within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Disable" }));
    expect(onAction).toHaveBeenCalledExactlyOnceWith("disable");
  });

  it("warns that erasing cannot be undone", async () => {
    render(<UserActions user={user({ state: "DELETED" })} selfId={SELF} can={everything} busy={false} onAction={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: "Erase for good" }));

    expect(await screen.findByRole("alertdialog")).toHaveTextContent(/cannot be brought back/i);
  });

  it("explains when there is nothing to do", () => {
    const { rerender } = render(<UserActions user={user()} selfId={SELF} can={nothing} busy={false} onAction={vi.fn()} />);
    expect(screen.getByText("There is nothing you can do with this user.")).toBeInTheDocument();

    rerender(<UserActions user={user({ state: "DELETED", erasureBegun: true })} selfId={SELF} can={everything} busy={false} onAction={vi.fn()} />);
    expect(screen.getByText("Erasing this user is under way.")).toBeInTheDocument();
  });

  it("disables the buttons while a change is under way", () => {
    render(<UserActions user={user()} selfId={SELF} can={everything} busy onAction={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Disable" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
  });
});
