// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { UnverifiedBanner } from "@/features/identity/UnverifiedBanner";
import { CSRF_HEADER } from "@/lib/csrf";

import { json, problem, renderScreen, stubApi } from "./ui";

const { success, failure, reloadPage } = vi.hoisted(() => ({ success: vi.fn(), failure: vi.fn(), reloadPage: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: failure } }));
vi.mock("@/lib/reload", () => ({ reloadPage }));

describe("the notice for a user whose email address is not verified (ADR 0041)", () => {
  beforeEach(() => {
    success.mockClear();
    failure.mockClear();
    reloadPage.mockClear();
  });

  it("asks for the email again, through the API, with the CSRF header", async () => {
    const api = stubApi(() => new Response(null, { status: 204 }));
    renderScreen(<UnverifiedBanner />);

    await userEvent.click(screen.getByRole("button", { name: "Send the email again" }));

    await waitFor(() => expect(success).toHaveBeenCalled());
    const [call] = api.calls;
    expect(call).toMatchObject({ method: "POST", path: "/api/v1/me/verification-email" });
    expect(call?.headers.get(CSRF_HEADER)).toBeTruthy();
  });

  it("says so when the API refuses, for example because too many emails were asked for", async () => {
    stubApi(() => problem(429, "platform.rate_limited"));
    renderScreen(<UnverifiedBanner />);

    await userEvent.click(screen.getByRole("button", { name: "Send the email again" }));

    await waitFor(() => expect(failure).toHaveBeenCalledWith("Too many requests. Wait a moment and try again."));
    expect(success).not.toHaveBeenCalled();
  });

  it("refreshes the session before reloading, so the new token says the address is verified", async () => {
    const api = stubApi(() => new Response(null, { status: 204 }));
    renderScreen(<UnverifiedBanner />);

    await userEvent.click(screen.getByRole("button", { name: "I have verified" }));

    await waitFor(() => expect(reloadPage).toHaveBeenCalledOnce());
    expect(api.calls[0]).toMatchObject({ method: "POST", path: "/auth/refresh" });
    expect(api.calls[0]?.headers.get(CSRF_HEADER)).toBeTruthy();
  });

  it("does not reload, and says what to do, when the session could not be refreshed", async () => {
    stubApi(() => json({ code: "bff.unauthenticated" }, 401));
    renderScreen(<UnverifiedBanner />);

    await userEvent.click(screen.getByRole("button", { name: "I have verified" }));

    await waitFor(() => expect(failure).toHaveBeenCalledWith(expect.stringContaining("Sign out and sign in again")));
    expect(reloadPage).not.toHaveBeenCalled();
  });
});
