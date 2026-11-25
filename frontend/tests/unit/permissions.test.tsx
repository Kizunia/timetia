// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Can, PermissionsProvider, usePermissions } from "@/features/identity/permissions";

import { meWith } from "./ui";

function Probe() {
  const { has, hasAny } = usePermissions();
  return (
    <p data-testid="probe">
      {String(has("access.roles.view"))} {String(has("access.roles.manage"))} {String(hasAny("x.y.z", "access.roles.view"))}{" "}
      {String(hasAny("x.y.z"))}
    </p>
  );
}

describe("permission-aware UI (usability only: the backend enforces every check)", () => {
  it("reports what the signed-in user holds", () => {
    render(
      <PermissionsProvider me={meWith(["access.roles.view"])}>
        <Probe />
      </PermissionsProvider>,
    );

    expect(screen.getByTestId("probe")).toHaveTextContent("true false true false");
  });

  it("shows a control to a user who holds its permission and hides it from one who does not", () => {
    const { unmount } = render(
      <PermissionsProvider me={meWith(["identity.users.invite"])}>
        <Can permission="identity.users.invite">
          <button type="button">Invite user</button>
        </Can>
      </PermissionsProvider>,
    );
    expect(screen.getByRole("button", { name: "Invite user" })).toBeInTheDocument();
    unmount();

    render(
      <PermissionsProvider me={meWith([])}>
        <Can permission="identity.users.invite">
          <button type="button">Invite user</button>
        </Can>
      </PermissionsProvider>,
    );
    expect(screen.queryByRole("button", { name: "Invite user" })).not.toBeInTheDocument();
  });

  it("refuses to be used without a provider, so a screen cannot silently show everything", () => {
    expect(() => render(<Probe />)).toThrow(/PermissionsProvider/);
  });
});
