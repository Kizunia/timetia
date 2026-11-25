// @vitest-environment jsdom
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ScopedRolesEditor } from "@/features/access/ScopedRolesEditor";

import { json, meWith, problem, renderScreen, stubApi } from "./ui";

const { success } = vi.hoisted(() => ({ success: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));

const USER = "22222222-2222-7222-8222-222222222222";
const DEPARTMENT = "aaaaaaaa-0000-7000-8000-00000000000a";

const roles = {
  items: [
    { id: "r-admin", name: "Administrator", builtIn: "ADMIN", permissions: [], scopeType: null },
    { id: "r-hod", name: "HOD", builtIn: null, permissions: [], scopeType: "DEPARTMENT" },
    { id: "r-faculty", name: "Faculty", builtIn: null, permissions: [], scopeType: "GLOBAL" },
  ],
  page: 0,
  size: 100,
  totalItems: 3,
  totalPages: 1,
};

const hod = { roleId: "r-hod", roleName: "HOD", scopeType: "DEPARTMENT", scopeId: DEPARTMENT, scopeCode: "CSE" };

const administrator = () => meWith(["access.roles.view", "access.roles.assign"], { administrator: true });

beforeEach(() => {
  success.mockClear();
});

function api(held: unknown[], answer?: (call: { method: string; path: string }) => Response | undefined) {
  return stubApi((call) => {
    const special = answer?.(call);
    if (special) return special;
    if (call.path === "/api/v1/roles") return json(roles);
    if (call.path.endsWith("/scoped-roles") || call.path.includes("/scoped-roles/")) return json(held);
    return problem(404, "platform.not_found");
  });
}

describe("the roles a user holds in a place", () => {
  it("lists each role with where it applies, by code, and everywhere for a global role", async () => {
    api([hod, { roleId: "r-faculty", roleName: "Faculty", scopeType: "GLOBAL", scopeId: null, scopeCode: null }]);
    renderScreen(<ScopedRolesEditor userId={USER} />, administrator());

    const rows = await screen.findAllByTestId("scoped-role-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("HOD");
    expect(rows[0]).toHaveTextContent("Department CSE");
    expect(rows[1]).toHaveTextContent("Faculty");
    expect(rows[1]).toHaveTextContent("Everywhere");
  });

  it("shows the list and no controls to someone who is not an administrator, even with the permission to assign roles", async () => {
    api([hod]);
    renderScreen(<ScopedRolesEditor userId={USER} />, meWith(["access.roles.view", "access.roles.assign"]));

    await screen.findByTestId("scoped-role-row");
    expect(screen.queryByRole("button", { name: /remove/i })).toBeNull();
    expect(screen.queryByRole("button", { name: "Add role" })).toBeNull();
    expect(screen.queryByLabelText("Role")).toBeNull();
  });

  it("says so when the user holds none", async () => {
    api([]);
    renderScreen(<ScopedRolesEditor userId={USER} />, administrator());

    expect(await screen.findByText("This user holds no roles in a place.")).toBeInTheDocument();
  });

  it("offers only the roles that apply in a place, and asks for a code for a department but not for an everywhere role", async () => {
    api([]);
    renderScreen(<ScopedRolesEditor userId={USER} />, administrator());

    const select = await screen.findByLabelText("Role");
    expect(within(select).getAllByRole("option").map((option) => option.textContent)).toEqual(["HOD", "Faculty"]);
    expect(screen.getByLabelText("Department code")).toBeEnabled();
    expect(screen.getByRole("button", { name: "Add role" })).toBeDisabled();

    await userEvent.selectOptions(select, "Faculty");
    expect(screen.getByLabelText("Applies to")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Add role" })).toBeEnabled();
  });

  it("adds one role by its code, with a POST that names only that role, and says so", async () => {
    const stub = api([hod]);
    renderScreen(<ScopedRolesEditor userId={USER} />, administrator());

    await userEvent.type(await screen.findByLabelText("Department code"), "  CSE ");
    await userEvent.click(screen.getByRole("button", { name: "Add role" }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Role added"));
    const post = stub.calls.find((call) => call.method === "POST");
    expect(post).toMatchObject({ path: `/api/v1/users/${USER}/scoped-roles`, body: { roleId: "r-hod", scopeCode: "CSE" } });
    expect(stub.calls.some((call) => call.method === "PUT")).toBe(false);
  });

  it("adds a role that applies everywhere without a code", async () => {
    const stub = api([]);
    renderScreen(<ScopedRolesEditor userId={USER} />, administrator());

    await userEvent.selectOptions(await screen.findByLabelText("Role"), "Faculty");
    await userEvent.click(screen.getByRole("button", { name: "Add role" }));

    await waitFor(() => expect(stub.calls.some((call) => call.method === "POST")).toBe(true));
    expect(stub.calls.find((call) => call.method === "POST")?.body).toEqual({ roleId: "r-faculty" });
  });

  it("removes one assignment by role and place", async () => {
    const stub = api([hod]);
    renderScreen(<ScopedRolesEditor userId={USER} />, administrator());

    await userEvent.click(await screen.findByRole("button", { name: "Remove HOD, Department CSE" }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Role removed"));
    const removal = stub.calls.find((call) => call.method === "DELETE");
    expect(removal?.path).toBe(`/api/v1/users/${USER}/scoped-roles/r-hod`);
    expect(removal?.query.get("scopeId")).toBe(DEPARTMENT);
  });

  it("explains a place that does not exist in words about the place, and keeps what was typed", async () => {
    api([], (call) =>
      call.method === "POST"
        ? problem(400, "platform.validation_failed", { errors: [{ field: "assignments", code: "unknown_scope", message: "x" }] })
        : undefined,
    );
    renderScreen(<ScopedRolesEditor userId={USER} />, administrator());

    const code = await screen.findByLabelText("Department code");
    await userEvent.type(code, "NOPE");
    await userEvent.click(screen.getByRole("button", { name: "Add role" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("There is no department, division or batch with that code.");
    expect(code).toHaveValue("NOPE");
  });

  it("explains that places cannot be checked yet when nothing provides them", async () => {
    api([], (call) => (call.method === "POST" ? problem(409, "access.scope_unvalidated") : undefined));
    renderScreen(<ScopedRolesEditor userId={USER} />, administrator());

    await userEvent.type(await screen.findByLabelText("Department code"), "CSE");
    await userEvent.click(screen.getByRole("button", { name: "Add role" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("cannot be checked yet");
  });
});
