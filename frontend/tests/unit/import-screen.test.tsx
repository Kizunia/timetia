// @vitest-environment jsdom
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ImportScreen } from "@/features/identity/ImportScreen";

import { json, meWith, problem, renderScreen, stubApi } from "./ui";

const { success } = vi.hoisted(() => ({ success: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));

beforeEach(() => {
  success.mockClear();
});

const COUNTS = { rows: 3, accountsCreated: 1, added: 2, alreadyPresent: 0, errors: 1, skipped: 0, notProcessed: 0 };

function line(overrides: Record<string, unknown>) {
  return {
    line: 2,
    email: "anita@college.example",
    role: "HOD",
    scope: "CSE",
    outcome: "ADDED",
    code: null,
    message: null,
    userId: null,
    accountCreated: true,
    ...overrides,
  };
}

function report(dryRun: boolean, overrides: Record<string, unknown> = {}) {
  return {
    dryRun,
    importId: dryRun ? null : "99999999-9999-7999-8999-999999999999",
    counts: COUNTS,
    lines: [
      line({}),
      line({ line: 3, email: "ravi@college.example", role: "Faculty", scope: "", accountCreated: false, outcome: "ALREADY_PRESENT" }),
      line({ line: 4, email: "bad@college.example", role: "Nonexistent", scope: "", accountCreated: false, outcome: "ERROR", code: "unknown_role", message: "There is no role with that name" }),
    ],
    ...overrides,
  };
}

function csv(name = "people.csv", bytes = 60): File {
  return new File(["email,role,scope\nanita@college.example,HOD,CSE\n".padEnd(bytes, " ")], name, { type: "text/csv" });
}

function importer(answer: (dryRun: boolean) => Response = (dryRun) => json(report(dryRun))) {
  return stubApi((call) => (call.path === "/api/v1/user-imports" ? answer(call.query.get("dryRun") === "true") : problem(404, "platform.not_found")));
}

const administrator = () => meWith(["identity.users.invite", "access.roles.assign"], { administrator: true });

describe("importing people from a file", () => {
  it("starts with nothing to send: both buttons wait for a file, and Import waits for the check", async () => {
    importer();
    renderScreen(<ImportScreen />, administrator());

    expect(screen.getByRole("heading", { name: "Import people" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Check the file" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Import" })).toBeDisabled();

    await userEvent.upload(screen.getByLabelText("CSV file"), csv());
    expect(screen.getByRole("button", { name: "Check the file" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Import" })).toBeDisabled();
  });

  it("checks the file first, which asks for a dry run, and says in words what each row would do", async () => {
    const stub = importer();
    renderScreen(<ImportScreen />, administrator());

    await userEvent.upload(screen.getByLabelText("CSV file"), csv());
    await userEvent.click(screen.getByRole("button", { name: "Check the file" }));

    const rows = await screen.findAllByTestId("import-row");
    expect(stub.calls).toHaveLength(1);
    expect(stub.calls[0]!.method).toBe("POST");
    expect(stub.calls[0]!.query.get("dryRun")).toBe("true");
    expect(String(stub.calls[0]!.body)).toContain(`name="file"`);
    expect(screen.getByText("What the file would do")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("Would add")).toBeInTheDocument();
    expect(rows[0]).toHaveTextContent("anita@college.example");
    expect(rows[0]).toHaveTextContent("new person");
    expect(within(rows[1]!).getByText("Already there")).toBeInTheDocument();
    expect(within(rows[2]!).getByText("Error")).toBeInTheDocument();
    expect(rows[2]).toHaveTextContent("There is no role with that name");
    expect(screen.getByTestId("import-summary")).toHaveTextContent("3 rows, 1 new person, 2 to add, 0 already there, 1 with errors");
    expect(screen.getByText(/Rows with an error are not applied/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Import" })).toBeEnabled();
  });

  it("imports the same file for real after the check, and says what was done", async () => {
    const stub = importer();
    renderScreen(<ImportScreen />, administrator());

    await userEvent.upload(screen.getByLabelText("CSV file"), csv());
    await userEvent.click(screen.getByRole("button", { name: "Check the file" }));
    await screen.findAllByTestId("import-row");
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    expect(await screen.findByText("What the import did")).toBeInTheDocument();
    expect(stub.calls.map((call) => call.query.get("dryRun"))).toEqual(["true", "false"]);
    expect(within(screen.getAllByTestId("import-row")[0]!).getByText("Added")).toBeInTheDocument();
    expect(success).toHaveBeenCalledWith("Import finished");
    // Once done, importing again needs a new check.
    expect(screen.getByRole("button", { name: "Import" })).toBeDisabled();
  });

  it("forgets the check when another file is chosen, so a file that was not checked is never imported", async () => {
    const stub = importer();
    renderScreen(<ImportScreen />, administrator());

    await userEvent.upload(screen.getByLabelText("CSV file"), csv("first.csv"));
    await userEvent.click(screen.getByRole("button", { name: "Check the file" }));
    await screen.findAllByTestId("import-row");

    await userEvent.upload(screen.getByLabelText("CSV file"), csv("second.csv"));
    expect(screen.queryAllByTestId("import-row")).toHaveLength(0);
    expect(screen.getByRole("button", { name: "Import" })).toBeDisabled();
    expect(stub.calls).toHaveLength(1);
  });

  it("does not offer to import a file that would add nothing", async () => {
    importer((dryRun) => json(report(dryRun, { counts: { ...COUNTS, accountsCreated: 0, added: 0, alreadyPresent: 3, errors: 0 } })));
    renderScreen(<ImportScreen />, administrator());

    await userEvent.upload(screen.getByLabelText("CSV file"), csv());
    await userEvent.click(screen.getByRole("button", { name: "Check the file" }));
    await screen.findAllByTestId("import-row");

    expect(screen.getByRole("button", { name: "Import" })).toBeDisabled();
  });

  it("refuses a file over 512 KB in the browser, with the reason, and sends nothing", async () => {
    const stub = importer();
    renderScreen(<ImportScreen />, administrator());

    await userEvent.upload(screen.getByLabelText("CSV file"), csv("big.csv", 513 * 1024));

    expect(screen.getByRole("alert")).toHaveTextContent("larger than 512 KB");
    expect(screen.getByRole("button", { name: "Check the file" })).toBeDisabled();
    expect(stub.calls).toHaveLength(0);
  });

  it("tells the administrator why a file could not be read at all, in the words the API gave", async () => {
    importer(() =>
      problem(400, "platform.validation_failed", { errors: [{ field: "file", code: "no_rows", message: "The file has a header but no rows." }] }),
    );
    renderScreen(<ImportScreen />, administrator());

    await userEvent.upload(screen.getByLabelText("CSV file"), csv());
    await userEvent.click(screen.getByRole("button", { name: "Check the file" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The file has a header but no rows.");
    expect(screen.queryAllByTestId("import-row")).toHaveLength(0);
  });

  it("says when another import is running", async () => {
    importer((dryRun) => (dryRun ? json(report(true)) : problem(409, "identity.import_running")));
    renderScreen(<ImportScreen />, administrator());

    await userEvent.upload(screen.getByLabelText("CSV file"), csv());
    await userEvent.click(screen.getByRole("button", { name: "Check the file" }));
    await screen.findAllByTestId("import-row");
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Another import is running"));
  });
});
