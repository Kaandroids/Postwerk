# Testing Strategy — Postwerk

Enterprise testing strategy mapped to this project's stack (Spring Boot · Angular ·
PostgreSQL/pgvector · Redis · Docker · REST). Each item notes purpose, what it covers
here, the tool, and current status: ✅ have · 🟡 partial · ❌ missing.

> **Current baseline:** Backend 472 tests (mostly Mockito unit/service). Testcontainers
> is on the classpath but only ~3 tests use Spring slices / containers. Frontend has
> 47 vitest unit tests (NOT yet run in CI) + 46 Playwright e2e specs (mock-first, in CI).
> CI has no coverage gating, lint, or security scanning.

## 1. Test Pyramid (functional)

### A. Unit (base — most numerous, fastest)
- **Backend unit** — pure business logic, executors, validator, utils; deps mocked (Mockito). ✅ strong (472)
- **Frontend unit** — pure utils, signal logic, services (vitest). 🟡 foundation laid (47); expand + add to CI
- **Mutation testing** — verifies tests actually catch regressions (PIT / Stryker). ❌

### B. Integration (middle)
- **Repository slice** (`@DataJpaTest` + Testcontainers) — custom `@Query`s and Flyway
  migrations against real PostgreSQL+pgvector. ❌ infra ready, unused — high priority
- **Web/Controller slice** (`@WebMvcTest`) — controller→DTO→validation→serialization,
  security filters (401/403). 🟡 ~3, expand
- **Full-context** (`@SpringBootTest` + Testcontainers) — end-to-end service flow with
  real PG+Redis, transactions, cache. 🟡 sparse
- **API contract / REST** — endpoint contracts (rest-assured or MockMvc): schema, status
  codes, error bodies. 🟡 partial
- **Frontend HTTP service** — `HttpClientTestingController` for interceptors (JWT refresh),
  error mapping. ❌

### C. E2E (top — fewest, slowest, most realistic)
- **FE e2e (mock-first)** — Playwright, API-mocked UI flows. ✅ (46 specs)
- **Full-stack e2e (real backend)** — Docker Compose up, real BE+PG+Redis for critical
  flows (login→sync→run automation); validates the mocks reflect reality. ❌ — the
  blind spot of mock-first
- **Smoke / health** — post-deploy `/health` + critical-endpoint liveness. 🟡 healthcheck only

## 2. Cross-cutting (non-functional)
- **Contract testing (Pact)** — FE expectation vs BE response; catches mock drift. ❌
- **Performance / Load (k6, Gatling, JMeter)** — concurrent users, sync under load,
  rate-limiter stress, DB query timing. ❌
- **Security**
  - SAST (CodeQL, Semgrep) ❌
  - Dependency/SCA (`npm audit`, OWASP Dependency-Check, Dependabot) 🟡 manual only
  - Container scan (Trivy/Grype) ❌
  - DAST (ZAP) ❌
  - Secret scanning (gitleaks) ❌
- **Accessibility (automated)** — Playwright + `axe-core`, WCAG. ❌
- **Visual regression** — Playwright screenshots / Percy / Chromatic. ❌
- **Resilience/Chaos** — Gemini timeout, Redis down, DB disconnect scenarios. 🟡 circuit breaker exists, tests sparse
- **i18n parity** — DE+EN key parity (missing translation = build fail). ❌
- **Migration testing** — Flyway up/down + idempotency, schema validation. ❌

## 3. CI/CD pipeline (quality gates, in order)
1. Lint/format — ESLint+Prettier (FE), Checkstyle/Spotless (BE) ❌
2. Backend unit + integration ✅
3. **Frontend unit (vitest)** — ❌ not in CI (quickest win: add `ng test --no-watch` job)
4. Coverage measurement + threshold — JaCoCo (BE), vitest coverage (FE) ❌
5. Frontend build ✅
6. E2E (Playwright) ✅
7. Dependency + secret + container scan ❌
8. Docker image build + smoke ❌
9. Branch protection — no merge until all gates green ❓

## 4. Tooling & standards
- Coverage targets: critical layers (service/executor) 80%+, overall 70%+; higher for
  cost/quota/security code.
- Pyramid ratio: ~70% unit, ~20% integration, ~10% e2e.
- Flaky policy: retry + quarantine + tracking.
- Test data builders: `TestFixtures` exists ✅, extend.
- Determinism: pin `Date.now()`/random (Clock injection), `@Sql` seeds.

## 5. Prioritized roadmap for this project
| Priority | Item | Effort | Why |
|----------|------|--------|-----|
| P0 | Add frontend vitest to CI | 5 min | 47 tests exist but don't run — free win |
| P0 | Coverage gating (JaCoCo + vitest) | ½ day | Regression guard, visibility |
| P0 | Repository slice tests (`@DataJpaTest` + Testcontainers) | 1–2 d | Query + migration confidence; infra ready |
| P1 | `@WebMvcTest` security/contract tests | 1–2 d | Auth/authz/validation gates |
| P1 | Full-stack e2e (1–2 critical flows, real BE) | 1 d | Closes the mock-first blind spot |
| P1 | CI SAST + dependency + secret scanning | ½ day | CodeQL/Semgrep/gitleaks/Dependabot |
| P2 | axe a11y + i18n parity tests | ½ day | Regression guards |
| P2 | Load test (k6) on critical endpoints | 1 d | Rate-limiter + sync scale |
| P3 | Mutation testing (PIT/Stryker) on critical modules | 1 d | Test-quality audit |
| P3 | Container scan (Trivy) + visual regression | 1 d | Depth |

**Highest-ROI first three (P0):** add vitest to CI → coverage gating → repository slice
tests. Infra is ready, risk is low, value is high.
