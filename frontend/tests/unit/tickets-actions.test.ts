import { describe, expect, it } from "vitest";

import { availableActions, type AvailableActions } from "@/features/tickets/lib/actions";

import { HOD, ME, OTHER, review, ticket } from "./tickets-fixtures";

const me = (...permissions: string[]) => ({ id: ME, permissions });
const none = { edit: false, apply: false, submit: false, withdraw: false, review: null, escalate: false, revert: false, primary: null };

type Case = [name: string, t: Record<string, unknown>, permissions: string[], expected: Partial<AvailableActions>];

const REVERT = "rescheduling.tickets.revert";
const IN_SCOPE = "rescheduling.tickets.revert-in-scope";
const APPROVED = [review({ status: "APPROVED" })];
const CASES: Case[] = [
  // --- drafts -----------------------------------------------------------------------------------------------------
  ["a draft reschedule: apply, send, edit, withdraw; apply is the main button", {}, [],
    { edit: true, apply: true, submit: true, withdraw: true, primary: "apply" }],
  ["a draft raised on behalf of a teacher cannot be applied; sending it is the main action", { actingFacultyId: "f-1" }, [],
    { edit: true, apply: false, submit: true, withdraw: true, primary: "submit" }],
  ["a draft cancellation on behalf can be applied", { type: "CANCEL", actingFacultyId: "f-1" }, [],
    { edit: true, apply: true, submit: true, primary: "apply" }],
  ["a draft substitute", { type: "SUBSTITUTE" }, [], { edit: true, apply: true, submit: true, primary: "apply" }],
  ["a draft exchange", { type: "EXCHANGE" }, [], { edit: true, apply: true, submit: true, primary: "apply" }],
  ["a draft extra lecture", { type: "EXTRA" }, [], { edit: true, apply: true, submit: true, primary: "apply" }],
  ["a draft permanent change can neither be applied nor edited, only sent or withdrawn", { type: "PERMANENT_CHANGE" }, [],
    { edit: false, apply: false, submit: true, withdraw: true, primary: "submit" }],
  ["a draft revert can be applied but not edited", { type: "REVERT" }, [],
    { edit: false, apply: true, submit: true, primary: "apply" }],
  ["a draft revert on behalf can still be applied", { type: "REVERT", actingFacultyId: "f-1" }, [],
    { apply: true, primary: "apply" }],
  ["someone else's draft offers nothing", { authorUserId: OTHER }, [], none],
  // --- under review -----------------------------------------------------------------------------------------------
  ["under review with every answer approved: apply", { status: "IN_REVIEW", reviews: APPROVED }, [],
    { edit: true, apply: true, submit: false, withdraw: true, primary: "apply" }],
  ["under review with one answer missing: no apply", { status: "IN_REVIEW", reviews: [...APPROVED, review({ reviewerUserId: HOD })] }, [],
    { apply: false, withdraw: true, primary: null }],
  ["under review with nobody asked yet: no apply", { status: "IN_REVIEW", reviews: [] }, [], { apply: false }],
  ["a rejection in the round: no apply", { status: "IN_REVIEW", reviews: [review({ status: "REJECTED" })] }, [], { apply: false }],
  ["an acknowledgement counts as an answer", { status: "IN_REVIEW", reviews: [review({ status: "ACKNOWLEDGED" })] }, [], { apply: true }],
  ["answers of an earlier round do not count", { status: "IN_REVIEW", revisionNo: 2, reviews: [review({ status: "APPROVED", revisionNo: 1 })] }, [],
    { apply: false }],
  ["a permanent change approved only by a class representative cannot be applied",
    { type: "PERMANENT_CHANGE", status: "IN_REVIEW", reviews: [review({ capacity: "CR", status: "APPROVED" })] }, [], { apply: false }],
  ["a permanent change approved by the HOD can be applied",
    { type: "PERMANENT_CHANGE", status: "IN_REVIEW", reviews: [review({ capacity: "CR", status: "APPROVED" }), review({ reviewerUserId: HOD, capacity: "HOD", status: "APPROVED" })] },
    [], { apply: true, edit: false, primary: "apply" }],
  ["under review on behalf of a teacher, once approved, the author may apply it",
    { status: "IN_REVIEW", actingFacultyId: "f-1", reviews: APPROVED }, [], { apply: true }],
  // --- the person who was asked -----------------------------------------------------------------------------------
  ["a person with a pending review may approve, reject or acknowledge; reviewing is the main action",
    { status: "IN_REVIEW", authorUserId: OTHER, reviews: [review({ reviewerUserId: ME })] }, [],
    { review: { decisions: ["APPROVE", "REJECT", "ACKNOWLEDGE"] }, primary: "review", apply: false, withdraw: false }],
  ["the HOD of a permanent change may only approve or reject",
    { type: "PERMANENT_CHANGE", status: "IN_REVIEW", authorUserId: OTHER, reviews: [review({ reviewerUserId: ME, capacity: "HOD" })] }, [],
    { review: { decisions: ["APPROVE", "REJECT"] }, primary: "review" }],
  ["another reviewer of a permanent change may still acknowledge",
    { type: "PERMANENT_CHANGE", status: "IN_REVIEW", authorUserId: OTHER, reviews: [review({ reviewerUserId: ME, capacity: "CR" })] }, [],
    { review: { decisions: ["APPROVE", "REJECT", "ACKNOWLEDGE"] } }],
  ["a review already answered is not offered again",
    { status: "IN_REVIEW", authorUserId: OTHER, reviews: [review({ reviewerUserId: ME, status: "APPROVED" })] }, [], none],
  ["a review of an earlier round is not offered",
    { status: "IN_REVIEW", authorUserId: OTHER, revisionNo: 2, reviews: [review({ reviewerUserId: ME, revisionNo: 1 })] }, [], none],
  ["a review is only offered while the ticket is under review",
    { status: "REJECTED", authorUserId: OTHER, reviews: [review({ reviewerUserId: ME })] }, [], none],
  // --- rejected ---------------------------------------------------------------------------------------------------
  ["a rejected ticket: edit (main) and escalate, but no withdraw", { status: "REJECTED", reviews: [review({ status: "REJECTED" })] }, [],
    { edit: true, escalate: true, withdraw: false, apply: false, submit: false, primary: "edit" }],
  ["a rejected permanent change can be taken to the HOD but not edited", { type: "PERMANENT_CHANGE", status: "REJECTED" }, [],
    { edit: false, escalate: true, primary: null }],
  ["someone else's rejected ticket offers nothing", { status: "REJECTED", authorUserId: OTHER }, [], none],
  // --- applied: revert --------------------------------------------------------------------------------------------
  ["an applied reschedule with the revert permission may be reverted", { status: "APPLIED" }, [REVERT], { revert: true, primary: null }],
  ["an applied reschedule with only the in-scope revert permission may be reverted", { status: "APPLIED", authorUserId: OTHER }, [IN_SCOPE], { revert: true }],
  ["an applied reschedule without either permission cannot be reverted", { status: "APPLIED" }, [], { revert: false }],
  ["an applied permanent change needs the in-scope permission", { type: "PERMANENT_CHANGE", status: "APPLIED" }, [REVERT], { revert: false }],
  ["an applied permanent change may be reverted by an HOD in scope", { type: "PERMANENT_CHANGE", status: "APPLIED" }, [IN_SCOPE], { revert: true }],
  ["an extra lecture is cancelled, not reverted", { type: "EXTRA", status: "APPLIED" }, [REVERT, IN_SCOPE], { revert: false }],
  ["a ticket that has been reverted cannot be reverted again", { status: "APPLIED", revertedByTicketId: "t-2" }, [REVERT], { revert: false }],
  ["an applied cancellation, substitute and exchange may be reverted", { type: "CANCEL", status: "APPLIED" }, [REVERT], { revert: true }],
  // --- the end of the road ----------------------------------------------------------------------------------------
  ["a withdrawn ticket offers nothing", { status: "WITHDRAWN" }, [REVERT, IN_SCOPE], none],
  ["an expired ticket offers nothing", { status: "EXPIRED" }, [REVERT, IN_SCOPE], none],
  ["a reverted ticket offers nothing", { status: "REVERTED", revertedByTicketId: "t-2" }, [REVERT, IN_SCOPE], none],
];

describe("availableActions", () => {
  it.each(CASES)("%s", (_name, overrides, permissions, expected) => {
    expect(availableActions(ticket(overrides), me(...permissions))).toMatchObject(expected);
  });
});
