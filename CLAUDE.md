# Postwerk — SaaS Project

## Tech Stack
- **Backend:** Spring Boot 3.4+ (Java 21, Maven)
- **Frontend:** Angular 19+ (Standalone components, Signals, SCSS)
- **Database:** PostgreSQL 17 with pgvector extension
- **Cache:** Redis 7+
- **Infrastructure:** Docker & Docker Compose
- **API Style:** REST (JSON), versioned under `/api/v1/`

## Principles
- **DRY** — No duplicated logic. Extract shared code into services/utilities.
- **SOLID** — Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion.
- **Separation of Concerns** — Backend handles business logic + data; Frontend handles UI + state.

## Project Structure
```
Postwerk/
├── backend/          # Spring Boot app
│   └── src/main/java/com/postwerk/
│       ├── config/        # Spring configs, security, Redis, CORS
│       ├── controller/    # REST controllers (thin, delegate to services)
│       ├── service/       # Business logic (interfaces + impl)
│       ├── repository/    # JPA repositories
│       ├── model/         # JPA entities
│       ├── dto/           # Request/Response DTOs
│       ├── mapper/        # Entity ↔ DTO mappers
│       ├── exception/     # Custom exceptions + global handler
│       └── util/          # Shared utilities
├── frontend/         # Angular app
│   └── src/app/
│       ├── core/          # Singleton services, guards, interceptors
│       ├── shared/        # Reusable components, pipes, directives
│       ├── features/      # Feature-based design (each feature is self-contained)
│       ├── models/        # Shared TypeScript interfaces/types
│       └── environments/  # Environment configs
├── docker/           # Docker support files
└── docker-compose.yml
```

## Backend Conventions
- Java 21, use records for DTOs
- All business logic in service layer (controllers stay thin)
- Service interfaces with single implementation unless polymorphism needed
- Use `@RestControllerAdvice` for global exception handling
- Repository layer: Spring Data JPA, custom queries via `@Query`
- Config via `application.yml` with env variable placeholders
- Profiles: `dev`, `prod`

## Frontend Conventions
- Standalone components only (no NgModules)
- Use Angular Signals for reactive state (`signal()`, `computed()`, `effect()`)
- SCSS for styling, use variables for theming
- Lazy-loaded feature routes
- HTTP calls through dedicated service classes
- Use `inject()` function, not constructor injection
- Barrel exports (`index.ts`) per feature folder

## Commands
- **Start all services:** `docker compose up -d`
- **Stop all services:** `docker compose down`
- **Rebuild after changes:** `docker compose up -d --build`
- **Backend only (dev):** `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- **Frontend only (dev):** `cd frontend && ng serve`
- **Run backend tests:** `cd backend && ./mvnw test`
- **Run frontend tests:** `cd frontend && ng test`
- **Run E2E tests:** `cd frontend && npx playwright test`

## Environment
- All secrets and config in `.env` (never committed)
- `.env.example` documents required variables
- Docker Compose reads `.env` automatically

## AI Prompt Templates (`backend/src/main/resources/prompts/`)

All AI system prompts are externalized into template files, loaded by `PromptService`.

| File | Purpose | Placeholders |
|------|---------|-------------|
| `assistant-system.txt` | AI assistant behavior rules & guidelines | None (static) |
| `tools-reference.txt` | AI tool list + tool-specific docs + automation testing (node config moved to `node-config-reference.txt`) | None (static) |
| `node-config-reference.txt` | **Shared** node config reference (per-node config JSON, FILTER DNF rule, sourceVariables, edge handles, injected variables, layout). Loaded by BOTH the assistant (`SystemPromptBuilder`) and the wizard build phase (`WizardServiceImpl`) so the two surfaces describe nodes identically. | None (static) |
| `wizard-chat-de.txt` | Wizard chatting phase (German) | None |
| `wizard-chat-en.txt` | Wizard chatting phase (English) | None |
| `wizard-build.txt` | Wizard building phase orchestration (steps, wizard context — no logged-in user → `placeholder` accountIds); node config comes from `node-config-reference.txt` | `{{lang}}` |
| `classify.txt` | Email classification prompt | `{{emailText}}`, `{{categories}}` |
| `kb-judge.txt` | Knowledge-base match judge (VECTOR_SEARCH node): pick best candidate or `no_match` | `{{query}}`, `{{candidates}}` |

### PromptService (`service/PromptService.java`)
- Loads `.txt` templates from classpath `/prompts/` directory
- Resolves `{{placeholder}}` syntax with provided `Map<String, String>` variables
- Templates cached in `ConcurrentHashMap` (loaded once, immutable at runtime)
- Two overloads: `load(name, vars)` and `load(name)` (no-var)
- Used by: `AiAssistantServiceImpl`, `WizardServiceImpl`, `GeminiServiceImpl`

### When modifying AI behavior
- **Behavior rules** (how to respond, planning phase, warnings) → edit `assistant-system.txt`
- **Tool documentation** (schemas, parameters, node types, edge handles) → edit `tools-reference.txt`
- **Wizard persona** → edit `wizard-chat-de/en.txt` or `wizard-build.txt`
- **Email classification** → edit `classify.txt`
- **Dynamic content** (user resources JSON, phase instructions) stays in Java code

## Workspace Scoping
- `WorkspaceService` holds the active email account via signals
- All workspace-scoped queries must use `workspaceService.activeAccount()?.id`

## Key Architecture Concepts

### Email Account Passwords
- Encrypted at rest using AES-256-GCM (`EncryptionConfig`, `ENCRYPTION_KEY` env var)
- Never returned in API responses

### Filter System (DNF — Disjunctive Normal Form)
- Between groups: **OR**, within group: **AND**
- `EmailFilterCondition.groupIndex` separates groups, `sortOrder` orders them

### Cost-Based AI Quota System
- AI usage tracked by **cost** (not raw token count), `costMicros` per call (1 USD = 1M micros)
- Plans define `costLimitCents`: `-1` = unlimited, `0` = AI disabled, `>0` = monthly EUR cent cap
- `QuotaServiceImpl.checkAiTokenQuota()` enforces limits
- Topbar AI limiter widget shows usage percentage

### GDPR Data Retention (DSGVO/TTDSG)
- `DataRetentionService` runs daily at 02:00 — email retention (365d), conversation retention (90d), IP pseudonymization (90d), audit log retention (730d)
- Config: `GdprProperties` (`app.gdpr.*` in `application.yml`)
- AI conversations: soft-delete (`deletedAt` field, `@SQLRestriction`)
- Full audit: `doc/GERMAN_LAW_COMPLIANCE.md`

### Role System & Admin
- `Role` enum: `USER`, `ADMIN` — stored in JWT, checked via `TokenService.isAdmin()`
- Admin routes: `/api/v1/admin/**` protected with `hasRole("ADMIN")`
- Frontend: `adminGuard`, `AdminService`, admin pages under `/dashboard/admin/*`
- Plans: STARTER (AI disabled), PRO (€5 cap), ENTERPRISE (unlimited) — no payment integration yet

### AI Assistant Chat
- **Backend:** `AiAssistantController` → `AiAssistantServiceImpl` → `AiToolRegistry` → Google Gemini API
- **Frontend:** `AiChatService` (SSE streaming) → `AiChatPanelComponent` (slide-in panel)
- **Phases:** `OPEN` → `PLANNING` (automation write tools blocked) → `BUILDING` (unlocked)
- **27 tools** in `AiToolRegistry`, **7 tools** in `WizardToolRegistry` (see `tools-reference.txt` for details)
- SSE events: `tool_start`, `tool_result`, `reply`, `phase`, `done`, `error`, `cancelled`

### Wizard (Public Onboarding)
- Public (no JWT), session in Redis (30min TTL), temp UUIDs until `claimSession`
- Phases: `chatting` → `building` → `ready`
- Custom read-only canvas (no Foblex Flow) with email packet animations
- Must converse 2+ exchanges before building

## Theme System & CSS Variables
- **Definition:** `frontend/src/styles/_themes.scss`
- **Service:** `ThemeService` — `theme` (`light`/`dark`), `darkVariant` (`slate`/`warm`/`plum`/`black`)
- **MUST use semantic variables:** `--bg`, `--bg-2`, `--fg`, `--fg-muted`, `--fg-subtle`, `--border`, `--border-strong`, `--accent`, `--success`, `--danger`, `--warning`
- **NEVER** hardcode hex colors — use `var(--success)`, `var(--danger)`, etc.
- **Badge backgrounds:** `color-mix(in srgb, var(--success) 14%, var(--bg-2))` — mix with `var(--bg-2)` not `transparent`

## Frontend UI/UX Patterns
- **Form pages:** Two-column grid (left: form, right: sticky info panel with gradient cards)
- **List → Form:** Toggle via `view = signal<'list' | 'form'>('list')`
- **Color picker:** 8 swatches + custom picker (same in categories/filters/email-accounts)
- **Condition builder:** Small uppercase labels above segments, 36px row height, group cards with left border color
- **Input height:** Main form 44px, condition rows 36px
- **Empty state:** Dashed border, icon, CTA button
- **i18n:** All UI text via `I18nService.t(key)`, always add both DE + EN keys

## Foblex Flow (`@foblex/flow`) — Automation Editor
- `<f-background>` must be a **sibling** of `<f-canvas>`, NOT a child
- Single `<f-rect-pattern>` inside `<f-background>` (does not support multiple patterns)
- Do NOT add grid via CSS — use Foblex's SVG pattern system
- **Connector shapes:** Circle = email flow, Diamond = template/extraction flow; Hollow = input, Filled = output
- **Node cards:** Left accent border (3px, `var(--nc)`), tinted header, type label in node color

## Implementation Checklist

### New Feature
```
Backend:  Migration (Flyway V{N}__*.sql) → Entity → DTO (record) → Service (interface+impl) → Controller → ./mvnw compile
Frontend: Model (.model.ts) → Service → Component (standalone, signals, data-testid, inject()) → i18n (DE+EN) → npx ng build
E2E:      mocks/*.mocks.ts → pages/*.page.ts → tests/*/*.spec.ts → barrel exports → npx playwright test
```

### Existing Feature Modified
```
Backend:  Update DTO/Service/Controller → fix test mocks if constructor changed → compile
Frontend: Update Component (+ data-testid) → update i18n if new text → build
E2E:      Update mock data → update page selectors → update test assertions → playwright test
```

### Automation Node Added/Modified
```
Backend:  Executor → dry-run resultDetail → resolveFieldValue() → compile
Frontend: STATUS_BY_TYPE → FlowNodeInfo → testPanelNodes → formatNodeDetails() → assertion dropdowns → build
AI:       Update tools-reference.txt if schema/behavior changed
E2E:      Mock resultDetail → playwright test
```

### E2E Test Rules
- **Playwright** with mock-first approach (all API calls mocked via `MockApi`)
- **Fixture pattern:** `authenticatedPage` (dashboard), `adminPage` (admin), bare `page` (auth)
- **Page Object Model:** `e2e/pages/*.page.ts` with `data-testid` selectors
- **Mocks:** `e2e/mocks/*.mocks.ts` — must match actual API response structure
- **Mock route order:** Specific patterns before generic (FIFO matching)

## Knowledge Base + Vector Search (`doc/KNOWLEDGE_BASE_DESIGN.md`)
Org-scoped reference data the `VECTOR_SEARCH` automation node searches. Full design + live status in the doc.
- **Model:** `KnowledgeBase` borrows its field schema from a `ParameterSet` (`parameterSetId`) + a `fieldRoles` JSONB overlay marking each field `embed` (semantic) / `keyword` (full-text). `KnowledgeBaseEntry` = a filled instance: `data` JSONB + `embedding vector(3072)` (no ANN index — small KBs, seq scan; reuses `EmbeddingService`/Category pattern) + `search_text` (GIN expression index) + `embedding_dirty`. Migration **V82**.
- **Embedding** is async: saves set `embedding_dirty`; `KnowledgeBaseEmbeddingWorker` (`@Scheduled`) batch-embeds off the request thread. `KbContentBuilder` derives embed/keyword text + content hash (shared by service + worker).
- **Search** (`KnowledgeBaseSearchService`): hybrid cosine (`<=>`) + Postgres full-text → **reciprocal rank fusion** → top-K → `GeminiService.match` judge (`kb-judge.txt`) → confidence threshold. Returns `KbSearchResult` (`MATCHED`/`NOT_MATCHED`/`ERROR`).
- **Node** `VECTOR_SEARCH` (`VectorSearchNodeProcessor`): config `{knowledgeBaseId, queryVariable, topK, confidenceThreshold}`; **3 statuses → 2 handles** — `MATCHED`→`success`, `NOT_MATCHED`/`ERROR`→`fail` (status distinguishes them in traces). Injects `vectorsearch_<nodeId>.match.<field>` / `.confidence` / `.reason`. Mockable in dry-run. Validator codes `VECTOR_SEARCH_NO_KB` / `VECTOR_SEARCH_NO_QUERY` (BE `AutomationValidator` + FE `AutomationLintService`).
- **Import:** CSV; KB's optional `uniqueField` drives upsert (hash-skip unchanged re-embed) vs full replace.
- **Frontend:** `knowledge-base.model.ts` + `knowledge-base.service.ts` + `knowledge-bases-page` (route `/dashboard/knowledge-bases`, sidebar Resources). Editor: `NODE_PALETTE`/`NODE_DEFAULT_CONFIG` + node-config-panel section + test-panel `STATUS_BY_TYPE`/`formatNodeDetails`. All `kb_*` / `auto_vs_*` i18n keys (DE+EN).
- **Marketplace (PLANNED, not built):** publish must snapshot the KB (schema; entries only if author opts into `FULL` share, else `SCHEMA_ONLY`); PRIVATE listing ⇒ hidden KB. Folded into the deferred marketplace-snapshot refactor (doc Phase 0 + 6b).

## Automation Marketplace
Users publish their automations as listings; others discover, install, configure and review them.
Five surfaces under `features/marketplace/`: **Discover · Detail · Publish · Configure · Library**.

### Install = hidden buyer-owned copy (core model)
- Installing **deep-copies** the source automation into the buyer's account AND **clones every
  referenced resource** (categories, parameter sets, templates, webhook endpoints) via an old→new ID
  map (`MarketplaceResourceCloner`), rewriting node `config` JSON and clearing `accountIds`. This makes
  execution "just work": buyer owns the copy → buyer's inbox/quota/SMTP.
- **PUBLIC** listing → editable copy (`hidden=false`, `status=PAUSED`), opened in the normal editor.
- **PRIVATE** listing → content-hidden copy (`hidden=true`, `locked=true`): ALL constants are copied so
  logic runs, but only an author-declared **publishable** subset is buyer-editable, via the Configure
  surface (value-only constants editor + email-account binding → Activate).
- **`Automation.hidden`** flag (migration `V59`): the automation get/flow API must refuse to return
  nodes/edges/constant values when `hidden=true` (buyer can never read a private copy's internals).
- **Payment = metadata-only.** Pricing model + price are stored; "acquire" creates an entitlement
  (`MarketplaceAcquisition`) without charging.

### Backend
- Migrations `V57__marketplace.sql` (listings/acquisitions/reviews/publishable_constants),
  `V58__add_marketplace_publish_limit.sql` (`plans.marketplace_publish_enabled`), `V59__automation_hidden.sql`.
- `MarketplaceService` (+impl) reuses `AutomationService.installCopy(buyerOrgId, buyerId, source, configRewriter, hidden, locked)`
  — install lands in the caller's **active** org (threaded from `X-Org-Id` through the controller), so the spend
  follows the org the install was made from (NOT the buyer's personal org). Duplicate-install guard + acquisition
  + cloned resources (`MarketplaceResourceCloner`) are all org-scoped on that active org.
  `saveAcquisitionConstants` rejects non-publishable names and encrypts secret values.
- Controller `/api/v1/marketplace/...`: `GET/POST /listings`, `GET /listings/{id}`,
  `POST /listings/{id}/install`, `DELETE /listings/{id}` (unpublish), `GET /library`,
  `PUT /acquisitions/{id}/constants`, `PUT /acquisitions/{id}/accounts`,
  `POST /acquisitions/{id}/activate`, `GET/POST /listings/{id}/reviews`.

### Frontend
- `core/services/marketplace.service.ts` + `models/marketplace.model.ts`. All `mkt_*` i18n keys (DE+EN).
- Marketplace styles live in a global stylesheet (`marketplace.scss`, all `.mk-` prefixed) imported via
  `styles.scss` — keeps them off the per-component style budget.
- Routes: `marketplace`, `marketplace/detail/:id`, `marketplace/publish`, `marketplace/configure/:id`,
  `marketplace-library` (sidebar `marketplaceNav` group).

## Git
- Conventional commits: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`
- Feature branches: `feature/<name>`, bugfix: `fix/<name>`
- **Commit frequently while building** — make small, logical, self-contained commits as you go (one per working step / file group), each one compiling/passing, instead of a single giant end-of-feature commit. Keeps the branch reviewable and easy to revert or bisect.
- **`main` is PROTECTED (GitHub ruleset "Main Protection"): direct pushes are rejected.** NEVER `git push` to `main`. For EVERY change — even one-line/docs/hotfix — branch first (`feature/*`, `fix/*`, `docs/*`), commit there, push the branch, then open a PR to merge into `main`. Required approvals = 0 (solo), so the author can merge their own PR after checks pass.
- Remotes: `origin` → `github.com/Kaandroids/Postwerk.git` (the live/public repo). The production VM deploys by pulling `main`.
