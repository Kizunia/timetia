# Batch 4: Shared Courses, Faculty Load and Permanent Changes

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

Fourth batch of business-logic decisions for the Lecture Rescheduling module. It builds on [Batch 1](batch-1-approval-roles-and-conflict-rules.md), [Batch 2](batch-2-reverts-same-slot-tickets-cancellation-and-requests.md) and [Batch 3](batch-3-rooms-notifications-and-timetable-versions.md). Where it differs from the module spec, **this document wins**.

## 1. Faculty on leave

- Nothing happens automatically when a faculty goes on leave. Their pending tickets stay as they are.
- The **HOD acts on their behalf**, as the module spec already allows (section 8.5: raise, cancel, substitute).

## 2. Shared courses

- A shared course is a course whose students come from several divisions. **PAS** is a regular theory lecture of this kind, so it is covered by the CR. **FCTC** runs fully online.
- **The "shared-course students busy" conflict check is skipped in the first version.** Shared courses are treated like ordinary division lectures for conflict checking.
- Because no student lists are read, students of other divisions are not looked up for notifications either. This limit is accepted for the first version and may be revisited.

## 3. Extra lectures and faculty load

- **Extra lectures count toward faculty load.**
- The load figures (Theory, Lab, Tutorial) stay **plain information** (Batch 1, section 6.3). There is no cap and nothing blocks.

## 4. Permanent changes

A permanent change repeats every week from a date onward. It still needs the **HOD** (Batch 1, section 4.3).

- **For now the module publishes the permanent change itself.** Once the HOD approves, it applies as a recurring change in the module's own change overlay, from the chosen date.
- It is **not written into the ERP**. The ERP base timetable is unchanged, and the module still emits the usual events for the affected occurrences.
- The owner has marked this **"for now"** and expects it may change.

### Tension to resolve later

The module spec says the module never writes to the ERP and never owns the base timetable. A permanent change kept only in the overlay means the module and the ERP show different weekly timetables until the ERP owner publishes a matching new version. The two views should be reconciled when the ERP integration is designed (module spec Q3). Batch 3 already keeps overlay changes and re-checks them when the ERP publishes a new version.

## 5. Changes to the module spec

| Spec section | Change |
| --- | --- |
| 10 Shared-course students busy | Skipped in the first version. |
| 7 Change types | A permanent change is a recurring overlay applied by the module after HOD approval, not a request to the ERP owner. |
| 5 Rule "never writes into ERP tables" | Unchanged. The overlay is still the module's own data. |
| 13 Q5 | Answered: extra lectures count toward load, with no cap. |

## 6. Still open

No question is open from the discussion so far. Remaining items from the module spec: the ERP API (Q3), and the final structure of the docs after migration.
