# 0006 Rejected tickets are edited and resubmitted

- **Type:** adr
- **Module:** lecture-rescheduling
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-09

## Context

The ticket lifecycle said **Rejected is final**: after a rejection the author raised a new ticket that copied the old one. The original spec said the author "can edit and resubmit". The data model audit ([research note](../../../research/data-model-audit.md)) found the two disagreeing and asked the owner to choose.

## Decision

A rejected ticket is **edited and resubmitted on the same ticket**. It goes Rejected, then Draft, then In review again.

- Each resubmission raises the ticket's **revision number** by one.
- Reviews belong to a revision. Only approvals for the **current revision** count toward applying. Earlier rejections stay visible as history.
- The author may also escalate a rejection to the HOD, who is added as a reviewer and sends the ticket straight back to In review.
- A rejected ticket whose lecture has already started cannot be resubmitted. It stays Rejected.
- Every edit and resubmission is written to the audit log with before and after values.

This replaces "Rejected is final" in the [ticket lifecycle](../rules/ticket-lifecycle.md) and the matching line in the [approval model](../rules/approval-model.md). Applied stays final: a revert is still a new ticket.

## Alternatives considered

A new ticket that copies the rejected one. Rejected: it spreads one request over several tickets, loses the link between a rejection and its fix, and was never what the original spec said.

## Consequences

The ticket and review records need a revision number (see [data model fixes](../../../research/data-model-fixes.md), F-B3). Conflict checks and expiry run again on every resubmission.

## Status

Accepted, 2026-11-09.
