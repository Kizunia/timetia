import { describe, expect, it } from "vitest";

import {
  CAPACITY_LABEL,
  DAY_LABEL,
  MODE_LABEL,
  REVIEW_STATUS_LABEL,
  ROOM_TYPE_LABEL,
  SESSION_LABEL,
  TICKET_STATUS_LABEL,
  TICKET_STATUS_VARIANT,
  TICKET_TYPE_LABEL,
} from "@/lib/labels";

// The maps are checked against the generated API types at compile time (`satisfies`); these check the words.
describe("labels", () => {
  it("has words for every ticket type and status", () => {
    for (const type of ["RESCHEDULE", "CANCEL", "SUBSTITUTE", "EXTRA", "EXCHANGE", "REVERT", "PERMANENT_CHANGE"] as const) {
      expect(TICKET_TYPE_LABEL[type]).toBeTruthy();
    }
    for (const status of ["DRAFT", "IN_REVIEW", "APPLIED", "REJECTED", "WITHDRAWN", "EXPIRED", "REVERTED"] as const) {
      expect(TICKET_STATUS_LABEL[status]).toBeTruthy();
      expect(TICKET_STATUS_VARIANT[status]).toBeTruthy();
    }
  });

  it("has words for reviews, sessions, modes and rooms", () => {
    expect(Object.values(REVIEW_STATUS_LABEL)).toHaveLength(4);
    expect(Object.values(CAPACITY_LABEL)).toHaveLength(5);
    expect(SESSION_LABEL).toEqual({ THEORY: "Theory", LAB: "Lab", TUTORIAL: "Tutorial" });
    expect(MODE_LABEL.ONLINE).toBe("Online");
    expect(ROOM_TYPE_LABEL.TUTORIAL_ROOM).toBe("Tutorial room");
  });

  it("indexes days from Monday = 1", () => {
    expect(DAY_LABEL[1]).toBe("Monday");
    expect(DAY_LABEL[6]).toBe("Saturday");
  });
});
