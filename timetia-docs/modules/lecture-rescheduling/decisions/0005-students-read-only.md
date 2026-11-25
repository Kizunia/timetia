# 0005 Students have read-only access

- **Type:** adr
- **Module:** lecture-rescheduling
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-16

## Context

Students need to see the live timetable, not change it.

## Decision

Students are users with read-only access to the live timetable and receive notifications. They see results of changes, not tickets. Student representatives (CR/BR) have the review powers described in [permissions](../rules/permissions.md).

## Alternatives considered

Let students raise tickets. Rejected: CR/BR use issues instead.

## Consequences

See [student flow](../flows/student.md).

## Status

Accepted, 2026-10-16.
