# 0004 Changes are stored as an overlay on the base timetable

- **Type:** adr
- **Module:** lecture-rescheduling
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-16

## Context

The base timetable is a weekly pattern. Changes are dated and temporary.

## Decision

The module stores only the changes on top of the base timetable. The base timetable owner was the ERP; in version 1 it is the module itself, with manual seeding (see 0001).

## Alternatives considered

Edit the base timetable in place. Rejected: it loses history and the weekly pattern.

## Consequences

See [change overlay](../../../shared/glossary/change-overlay.md).

## Status

Accepted, 2026-10-16.
