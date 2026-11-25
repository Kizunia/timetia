// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AVATAR_MAX_BYTES, checkAvatarFile } from "@/features/identity/avatar";
import { ProfileScreen } from "@/features/identity/ProfileScreen";
import { avatarUrl, initials, UserAvatar } from "@/features/identity/UserAvatar";
import { CSRF_HEADER } from "@/lib/csrf";

import { json, meWith, problem, renderScreen, stubApi } from "./ui";

const { success, refresh } = vi.hoisted(() => ({ success: vi.fn(), refresh: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh }) }));

const ME_ID = "11111111-1111-7111-8111-111111111111";
const VERSION = "33333333-3333-7333-8333-333333333333";

function picture(name = "me.png", type = "image/png", bytes = 100): File {
  return new File([new Uint8Array(bytes)], name, { type });
}

describe("what the browser checks before it sends an avatar", () => {
  it("accepts JPEG, PNG and WebP of up to 5 MB", () => {
    for (const type of ["image/jpeg", "image/png", "image/webp"]) {
      expect(checkAvatarFile({ type, size: 1000 })).toBeNull();
    }
    expect(checkAvatarFile({ type: "image/png", size: AVATAR_MAX_BYTES })).toBeNull();
  });

  it("refuses other types, files over 5 MB and empty files, each with its own words", () => {
    expect(checkAvatarFile({ type: "image/gif", size: 1000 })).toMatch(/JPEG, PNG or WebP/);
    expect(checkAvatarFile({ type: "image/svg+xml", size: 1000 })).toMatch(/JPEG, PNG or WebP/);
    expect(checkAvatarFile({ type: "", size: 1000 })).toMatch(/JPEG, PNG or WebP/);
    expect(checkAvatarFile({ type: "image/png", size: AVATAR_MAX_BYTES + 1 })).toMatch(/smaller than 5 MB/);
    expect(checkAvatarFile({ type: "image/png", size: 0 })).toMatch(/empty/);
  });
});

describe("the avatar picture", () => {
  it("makes initials from the first two words of the name", () => {
    expect(initials("Alice Example")).toBe("AE");
    expect(initials("  alice   b  c ")).toBe("AB");
    expect(initials("Madonna")).toBe("M");
    expect(initials("")).toBe("?");
    expect(initials("Émile Zola")).toBe("ÉZ");
  });

  it("is served from the user's own address with the size and the version, and the version is what refreshes it", () => {
    expect(avatarUrl(ME_ID, "small", VERSION)).toBe(`/api/v1/users/${ME_ID}/avatar?size=SMALL&v=${VERSION}`);
    expect(avatarUrl(ME_ID, "large", VERSION)).toContain("size=LARGE");
  });

  it("shows the initials when there is no avatar", () => {
    render(<UserAvatar userId={ME_ID} name="Alice Example" version={null} />);

    expect(screen.getByTestId("avatar")).toHaveTextContent("AE");
    expect(screen.queryByRole("img")).toBeNull();
    expect(document.querySelector("img")).toBeNull();
  });

  it("shows the image, and the initials again when it cannot be loaded", () => {
    const { container } = render(<UserAvatar userId={ME_ID} name="Alice Example" version={VERSION} size="large" />);
    const image = container.querySelector("img");
    expect(image).not.toBeNull();
    expect(image?.getAttribute("src")).toBe(avatarUrl(ME_ID, "large", VERSION));
    expect(image?.getAttribute("alt")).toBe("");

    fireEvent.error(image!);

    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByTestId("avatar")).toHaveTextContent("AE");
  });

  it("is announced only when it is given a label, because the name usually sits beside it", () => {
    const { rerender } = render(<UserAvatar userId={ME_ID} name="Alice Example" version={null} />);
    expect(screen.getByTestId("avatar")).toHaveAttribute("aria-hidden", "true");

    rerender(<UserAvatar userId={ME_ID} name="Alice Example" version={null} label="Your photo" />);
    expect(screen.getByRole("img", { name: "Your photo" })).toBeInTheDocument();
  });
});

describe("the photo card on the profile page (ADR 0029)", () => {
  beforeEach(() => {
    success.mockClear();
    refresh.mockClear();
  });

  const screenFor = (overrides = {}) =>
    renderScreen(<ProfileScreen accountUrl="http://localhost:8180/account" />, meWith([], overrides));

  it("sends a chosen photo to the API with the CSRF header, then says so and refreshes the page", async () => {
    const api = stubApi(() => json({ id: ME_ID }));
    screenFor();

    await userEvent.upload(screen.getByTestId("avatar-input"), picture());

    await waitFor(() => expect(success).toHaveBeenCalledWith("Photo saved"));
    expect(refresh).toHaveBeenCalledOnce();
    const [call] = api.calls;
    expect(call).toMatchObject({ method: "PUT", path: "/api/v1/me/avatar" });
    expect(call?.headers.get(CSRF_HEADER)).toBeTruthy();
    expect(call?.headers.get("content-type") ?? "").not.toContain("application/json");
  });

  it("does not send a file the browser can already tell is wrong, and says why", async () => {
    const api = stubApi(() => json({}));
    screenFor();
    const user = userEvent.setup({ applyAccept: false });

    await user.upload(screen.getByTestId("avatar-input"), picture("notes.txt", "text/plain"));
    expect(await screen.findByTestId("avatar-refusal")).toHaveTextContent("JPEG, PNG or WebP");

    await user.upload(screen.getByTestId("avatar-input"), picture("huge.png", "image/png", AVATAR_MAX_BYTES + 1));
    expect(await screen.findByTestId("avatar-refusal")).toHaveTextContent("smaller than 5 MB");
    expect(api.calls).toHaveLength(0);
    expect(refresh).not.toHaveBeenCalled();
  });

  it("takes the API's word over its own: a refused upload shows the reason chosen from the code", async () => {
    stubApi(() => problem(415, "files.unsupported_type"));
    screenFor();

    await userEvent.upload(screen.getByTestId("avatar-input"), picture());

    expect(await screen.findByRole("alert")).toHaveTextContent("Upload a JPEG, PNG or WebP image.");
    expect(refresh).not.toHaveBeenCalled();
    expect(success).not.toHaveBeenCalled();
  });

  it.each([
    [413, "files.too_large", "larger than 5 MB"],
    [400, "files.invalid_image", "could not be read as an image"],
    [429, "platform.rate_limited", "Too many requests"],
    [503, "platform.service_unavailable", "not available right now"],
  ])("explains a %i answer (%s)", async (status, code, words) => {
    stubApi(() => problem(status, code));
    screenFor();

    await userEvent.upload(screen.getByTestId("avatar-input"), picture());

    expect(await screen.findByRole("alert")).toHaveTextContent(words);
  });

  it("can choose the same file again after a failure", async () => {
    let answers = 0;
    const api = stubApi(() => (answers++ === 0 ? problem(503, "platform.service_unavailable") : json({ id: ME_ID })));
    screenFor();
    const file = picture();

    await userEvent.upload(screen.getByTestId("avatar-input"), file);
    await screen.findByRole("alert");
    await userEvent.upload(screen.getByTestId("avatar-input"), file);

    await waitFor(() => expect(success).toHaveBeenCalledWith("Photo saved"));
    expect(api.calls).toHaveLength(2);
  });

  it("offers to remove the photo only when there is one, and removes it through the API", async () => {
    const none = screenFor();
    expect(screen.queryByRole("button", { name: "Remove photo" })).toBeNull();
    expect(screen.getByRole("button", { name: "Upload a photo" })).toBeEnabled();
    none.unmount();

    const api = stubApi(() => new Response(null, { status: 204 }));
    screenFor({ avatarVersion: VERSION });
    expect(screen.getByRole("button", { name: "Replace photo" })).toBeEnabled();

    await userEvent.click(screen.getByRole("button", { name: "Remove photo" }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Photo removed"));
    expect(api.calls[0]).toMatchObject({ method: "DELETE", path: "/api/v1/me/avatar" });
    expect(api.calls[0]?.headers.get(CSRF_HEADER)).toBeTruthy();
    expect(refresh).toHaveBeenCalledOnce();
  });

  it("is disabled, with the reason, until the email address is verified", () => {
    screenFor({ emailVerified: false, avatarVersion: VERSION });

    expect(screen.getByRole("button", { name: "Replace photo" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Remove photo" })).toBeDisabled();
    expect(screen.getByText("Verify your email address to change your photo.")).toBeInTheDocument();
  });

  it("is not there at all when the files module is switched off", () => {
    screenFor({ avatarsEnabled: false });

    expect(screen.queryByText("Photo")).toBeNull();
    expect(screen.queryByTestId("avatar-input")).toBeNull();
    expect(screen.getByText("Preferences")).toBeInTheDocument();
  });
});
