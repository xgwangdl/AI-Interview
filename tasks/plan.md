# Implementation Plan: P0 Privacy Closure (#15)

## Overview

Implement recording consent, account data export, and account deletion before promoting the public registration flow. The work is tracked in GitHub Issue #15 and will be delivered through `feature/15-privacy-consent-export-delete` as one reviewable privacy slice.

## Architecture Decisions

- Consent is server-authoritative: the backend owns the current version and stores an immutable acceptance record.
- Video answer storage and session start require recording consent; consent is checked before the first persisted recording.
- Account export returns structured JSON containing the account's supported business data and media metadata, never password hashes or raw media bytes.
- Account deletion removes media first, then deletes database-owned data in one transaction. A storage failure aborts before database deletion.
- JWT access is rechecked against the account row so a deleted account's old token stops working immediately.

## Task List

### Phase 1: Consent

- [x] Task 1: Add consent migration, entity, repository, service, and API.
- [x] Task 2: Enforce consent before interview recording.
- [x] Task 3: Add the consent dialog and retry flow in the web client.

### Checkpoint: Consent

- [x] Consent API tests pass.
- [x] Recording without consent is rejected with 428.
- [x] Consent survives page refresh and records version/time.

### Phase 2: Export and deletion

- [x] Task 4: Add account data export with tests.
- [x] Task 5: Add media cleanup, account deletion, and deleted-token rejection tests.
- [x] Task 6: Add export/delete controls to personal settings.

### Checkpoint: Export and deletion

- [x] Export contains only the current account's data.
- [x] Delete removes media and database rows.
- [x] Old token returns 401 after deletion.

### Phase 3: Verification and documentation

- [x] Task 7: Add Flyway integration coverage and update user/architecture/deployment docs.
- [x] Task 8: Run backend/frontend tests and production build.

## Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Cross-module owner data missed during deletion | High | Delete by an explicit table inventory and test each domain. |
| Media deletion succeeds but DB transaction fails | Medium | Deletion order is media first; subsequent retry is idempotent. |
| Migration number collides with uncommitted scoring work | High | This branch uses V22; the scoring branch must renumber to V23 before merge. |
| Export leaks password hashes or other users' data | Critical | Use explicit queries and owner filters; never export `code`/`invitation_code`. |
| Consent gate blocks existing users unexpectedly | Medium | Show a clear consent dialog and allow retry without leaving the device-check flow. |

## Open Questions

- None for the P0 slice. Account export is JSON; downloadable report PDFs remain a later enhancement.
