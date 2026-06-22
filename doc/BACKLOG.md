# Engineering Backlog

Deliberately-deferred work, kept as a short living note. Everything here is a
considered "later", not an unknown gap. The verbose point-in-time audit reports
that previously lived in `doc/` were resolved or folded into this list and removed
(see git history if you need the original analysis).

## Performance (low urgency)
- **`EmailRepository.findFiltered()` correlated subqueries → `LEFT JOIN`.** Only
  matters for very large mailboxes; the rewrite is delicate (DNF filter
  correctness) so it was deliberately deferred. Needs careful query tests before
  changing.
- **Pagination for category & template lists.** Both currently return all rows.
  Real users hold well under the point where this matters (~50 items); revisit
  only if that assumption breaks. Changes the API response shape (array → page),
  so it also touches the frontend services + e2e mocks.

## Bundle
- Chat panel is already lazy-loaded via `@defer` (its `marked` + DOMPurify deps
  ship in a separate chunk). The remaining initial weight is the Angular
  framework itself (~92 kB gzip); the budget warning threshold was set to a
  realistic value. Optional future lever: externalize the i18n dictionaries
  (~13 kB gzip) — risky (used app-wide on first render), low payoff.

## Test coverage
- A unit-test foundation exists (pure utils + the automation lint rule catalog;
  `ng test` green). Backend is well covered (470+ tests). Expanding to HTTP
  services and component-level `TestBed` tests is open-ended; add coverage
  opportunistically alongside feature work rather than as a big-bang pass.

## Production readiness (infra, not app code)
These are deployment/ops concerns that belong in the hosting/IaC layer, not the
application repo, so they can't be implemented generically here:
- TLS/HTTPS termination
- Metrics & monitoring (e.g. Prometheus/Grafana, structured log shipping)
- PostgreSQL backup & restore strategy
- Secrets management (Vault/KMS) instead of `.env`
