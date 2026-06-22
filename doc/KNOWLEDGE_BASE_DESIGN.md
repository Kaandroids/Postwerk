# Knowledge Base + Vector Search Node — Design & Implementation Plan

> Status: **PLANNED** (design locked, not yet implemented). Next migration: **V82**.
> Feature: org-scoped knowledge bases (KB) + a `VECTOR_SEARCH` automation node that
> retrieves candidates from a KB, lets an LLM pick the best match, and routes by confidence.

## 0. Implementation status (live)

- ✅ **Phase 1 — Backend foundation** (V82, `KnowledgeBase`/`KnowledgeBaseEntry`, repos). Compiles.
- ✅ **Phase 2 — `KnowledgeBaseService`** (CRUD + entries + CSV upsert/replace import + `KnowledgeBaseEmbeddingWorker` + `KbContentBuilder` + controller + 4 `AuditAction`s). Compiles.
- ✅ **Phase 3 — `KnowledgeBaseSearchService`** (hybrid cosine+full-text RRF → `GeminiService.match` judge → confidence routing) + `kb-judge.txt` + `KbSearchStatus`/`KbSearchResult`. Compiles.
- ✅ **Phase 4 — `VECTOR_SEARCH` node** (`NodeType` value, `VectorSearchNodeProcessor` w/ mock support + 3-status/2-handle routing + `vectorsearch_<id>.*` vars, `AutomationValidator` codes `VECTOR_SEARCH_NO_KB`/`_NO_QUERY` + dangling/namespace wiring). Compiles.
- ✅ **Phase 5 backend** — none needed: `resolveFieldValue` is generic (resolves `confidence`/`reason`/`match.*`); VECTOR_SEARCH is not an `ACTION_TYPE` so no sim-feed case (like CATEGORIZE).
- ✅ **Phase 5 frontend** — `STATUS_BY_TYPE` (`['MATCHED','NOT_MATCHED','ERROR']`) + `formatNodeDetails` (confidence/reason/match fields) for VECTOR_SEARCH.
- ✅ **Phase 6 — Frontend** — `knowledge-base.model.ts`, `knowledge-base.service.ts`, KB management page (list / config: paramset picker + field-role table + unique key / entries: inline CRUD + CSV import), route `/dashboard/knowledge-bases` + sidebar (Resources), `NODE_PALETTE` + `NODE_DEFAULT_CONFIG` + `VectorSearchNodeConfig`, node-config-panel VECTOR_SEARCH section (KB picker + query combobox + topK + threshold, lazy KB load), **canvas ports** (`fNodeInput` + `success`/`fail` `fNodeOutput` — handle ids match the backend, note `fail` not `failure`), `AutomationLintService` codes, i18n (DE+EN). `ng build` green.
- ✅ **Phase 6 nicety** — `testPanelNodes` exposes VECTOR_SEARCH assertable fields: `confidence`, `reason`, **and `match.<field>`** (from the cached `matchFields`) so `+ Feld-Prüfung` covers matched-entry fields, consistent with the variable graph.
- ✅ **Editor polish** — node-card body now shows the selected KB name (cached `knowledgeBaseName`, `auto_vs_no_kb` fallback); variable graph (`VariableGraphService`) emits `vectorsearch_<id>.confidence`/`.reason`/`.match.<field>` downstream.
- ✅ **AI / wizard authorability** — `SystemPromptBuilder` injects `knowledgeBases` into USER'S CURRENT RESOURCES (so the AI can reference a real `knowledgeBaseId`); `node-config-reference.txt` (shared assistant+wizard) + `tools-reference.txt` document VECTOR_SEARCH (config schema, `success`/`fail` handles, injected `vectorsearch_<id>.*` vars, test-status row, `VECTOR_SEARCH_NO_KB`/`_NO_QUERY` codes, decision-table row). The validator→AI self-correction loop already covers it (no change: `AiToolRegistry` runs `AutomationValidator`, which has the VECTOR_SEARCH cases).
- ✅ **Phase 0 + 6b — Marketplace snapshot (DONE, verified)** — **V83** (`marketplace_listing_snapshots`, `listings.source_automation_id`+`kb_share_policy`, `knowledge_bases.hidden`) + `MarketplaceListingSnapshot` entity/repo. `MarketplaceSnapshotManifest`/`NodeSpec`/`EdgeSpec` DTOs. `MarketplaceSnapshotService` (+impl): **capture** serializes automation+nodes+edges+referenced resources (Category/ParameterSet/Template/**KnowledgeBase** incl. FULL entries+embeddings, paramset cross-refs followed) into the manifest; **materialize** recreates buyer-owned resources (idMap, paramsets/categories first, then templates/KBs), rewrites node configs via `MarketplaceResourceCloner.rewriteConfig`, and builds the flow via new `AutomationService.createSnapshotCopy` (reuses `buildFlow`). Wired into `publish` (capture + `sourceAutomationId`/`kbSharePolicy`), `install` (**materialize-or-live-fallback** — pre-snapshot listings unaffected), `getDetail` (nodeFlow from manifest else live), `unpublish` (delete snapshot). PRIVATE listing ⇒ materialized KB `hidden` ⇒ `KnowledgeBaseServiceImpl.listEntries` returns empty. **Fixed a latent publish bug**: `validate(userId,…)` → `validate(organizationId,…)` (was never finding the automation; masked by mocked tests). Verified: **27 tests green** — 9 `MarketplaceServiceTest` (no regression), new `MarketplaceSnapshotIT` (publish → author deletes live automation+category → buyer install succeeds from snapshot with cloned resources), + 18 KB tests.
- ✅ **Phase 7 — E2E (KB)** — `e2e/mocks/knowledge-base.mocks.ts` + `e2e/pages/knowledge-base.page.ts` + `e2e/tests/knowledge-base/knowledge-base.spec.ts` (4 tests: list, create, entries+add, CSV import) — **all 4 pass** (`npx playwright test knowledge-base` green). Added `data-testid`s to the KB page. Barrels updated. (Marketplace e2e still pending the snapshot refactor.)

- ✅ **Backend tests (17, green)** — `KbContentBuilderTest` (4, pure), `KnowledgeBaseEntryRepositoryIT` (4, `@DataJpaTest` + pgvector Testcontainers — native SQL `<=>` on `vector(3072)`, `to_tsvector @@ plainto_tsquery`, `data ->> :field`, dirty-queue, bulk delete + V82 Flyway apply), `KnowledgeBaseSearchServiceImplTest` (6, RRF/judge/threshold/no-match/error), `KnowledgeBaseServiceImplTest` (3, KB_NO_EMBED_FIELD + unknown-field validation). Plus `MarketplaceSnapshotIT` (1, decoupling) + KB e2e (4). Full backend suite: **513 unit + 5 IT green**.

Verified: backend `mvnw compile` + `test-compile` green after every phase; frontend `ng build` green; KB native SQL runtime-verified against real pgvector (Testcontainers). The feature is end-to-end usable (create KB → add/import entries → async embed → add+configure VECTOR_SEARCH node → hybrid retrieve + judge + confidence routing). The marketplace snapshot refactor (Phase 0+6b) is DONE + verified. Author-controlled KB entry sharing is wired end-to-end: `PublishListingRequest.shareKbEntries` → `fullKbEntries = PRIVATE || shareKbEntries` → `kbSharePolicy` (PUBLIC defaults SCHEMA_ONLY, opt-in FULL; PRIVATE always FULL+hidden), with a publish-UI toggle (`mk-share-kb`). Marketplace e2e (34 specs incl. publish + share-KB toggle) green. **No gaps remaining.** Final verification: backend **513 unit + 5 IT green**, frontend `ng build` green, **38 e2e green** (4 KB + 34 marketplace).

## 1. Motivation

A workflow needs to evaluate an incoming value against a body of reference knowledge.
Example: EXTRACT pulls a position from an invoice (`"Logitech Klavye"`); we must map it to
the correct SKR 03 account number among ~1500 entries. Stuffing all entries into the LLM is
costly and degrades quality, so we **retrieve → LLM-judge → route by confidence**.

This is the classic *retrieve-then-rerank / semantic-match* pattern (cf. RAPTOR collapsed-tree,
LlamaIndex AutoMergingRetriever, LangChain ParentDocumentRetriever, Glean enterprise search).

## 2. Locked design decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | **No SYSTEM/shared KB.** All KBs org-scoped, user-created. | Query is plain `WHERE organization_id = :org`. Removes shared-scoping + "who pays" questions. |
| D2 | **KB schema = an existing `ParameterSet`** (`parameterSetId`), not new columns. | DRY: reuse the `{name,type,description}[]` field-schema already used by EXTRACT/INPUT/OUTPUT + its UI + variable graph. |
| D3 | Per-field roles live in a **KB-level overlay** (`fieldRoles` JSONB), not on ParameterSet. | Keep ParameterSet pure (embed/keyword are meaningless to EXTRACT). |
| D4 | KB entry = **filled instance** of the ParameterSet. `data` JSONB. | Schema = type, entry = instance. |
| D5 | Embedding = **reuse `EmbeddingService`** (`gemini-embedding-001`, **3072 dim**), cosine `<=>`, **no ANN index** (sequential scan). | Avg KB = 5–10 entries → seq scan instant. 3072>2000 can't be HNSW-indexed but doesn't need to be at this scale. Mirrors `CategoryRepository`. |
| D6 | Search = **hybrid**: cosine vector + Postgres `tsvector` full-text → **RRF** → topK (default 5, max 10). | Pure semantic misses exact codes (`4930`); accounting is precision-critical. |
| D7 | **Single LLM judge** picks best candidate → `confidence` + `reason`; may return **no_match**. | One call (not per-level descent). Allowed to reject all → fail path actually fires. |
| D8 | Node = **`VECTOR_SEARCH`** (retrieve+judge+route folded into one node, mirrors CATEGORIZE). **No separate AI Agent node yet.** | Constrained picker is cheaper/deterministic/testable. Same retrieval reusable as an agent tool later. |
| D9 | Routing: **3 statuses → 2 handles.** `MATCHED`→`success`; `NOT_MATCHED`(no match / conf<thr / empty KB)→`fail`; `ERROR`(Gemini down / KB missing)→`fail`. | `NOT_MATCHED` ≠ `ERROR` in traces though both route to `fail` — fixes the documented INTEGRATION_CALL panel gap. No new enum values (reuse `MATCHED/NOT_MATCHED/ERROR`). |
| D10 | Re-import: **optional `uniqueField`.** None → full replace. Set → hash-based upsert (re-embed only changed rows). | Zero friction for tiny manual KBs; correct + cheap for SKR (`konto_nr` is the natural key). |
| D11 | Bulk import = **batch embedding** + async re-embed via `embedding_dirty` flag (off the request thread). | 1500 entries = rate-limit/cost/time; never inline. |
| D12 | **A KB is a cloned resource** in `MarketplaceResourceCloner` (joins Category/ParameterSet/Template). The `knowledgeBaseId` in node config is remapped via the old→new ID map; the KB's `parameterSetId` is cloned first (like templates clone their own paramset). | Install must "just work" → buyer owns the copy. Generic UUID discovery already picks up `knowledgeBaseId`. |
| D13 | **Publisher declares entry-sharing per referenced KB:** `SCHEMA_ONLY` (default — shell only, no entries; buyer fills/imports own) vs `FULL` (shell + entries with embeddings **copied verbatim**, no re-embed, zero buyer quota). PRIVATE listing ⇒ KB is `hidden`/`locked` (mirrors `Automation.hidden`) ⇒ entries API refuses to return content (runs, not readable). | KB data is sometimes the product (SKR 03), sometimes proprietary/PII. Default must never leak data. `cloneCategory` already copies embeddings verbatim, so FULL is free of re-embed cost. |
| D14 | **Publish takes an immutable snapshot; install materializes from the snapshot, not the author's live data** (chosen: done in the same package as KB). The author may freely edit/delete their live automation + resources afterward without affecting any listing or future install. KB schema (and, for `FULL`, entries+embeddings) are frozen into the snapshot at publish. | Listings currently hold a *live* reference (`automationId`) → deleting/editing referenced resources silently breaks installs (cloner skips missing resources). Snapshot is the complete fix (covers deletion **and** edit-drift **and** accurate advertised `nodeCount`/io facts) and makes per-resource delete guards unnecessary — even the existing `Automation` delete guard (`AutomationServiceImpl:238`) is retired. Gives real `version` semantics. |

### Caveats baked into implementation
- **LLM confidence is not calibrated** → optionally gate on *both* retrieval score and judge confidence; surface "this is a model estimate" in UI; let users tune the threshold empirically.
- **Prompt injection**: query comes from attacker-controlled `email.body`. Constrain judge output to **one of the candidate IDs** (structured output) so a malicious email can't inject arbitrary downstream values.
- **Quota/cost**: both the query embedding and the judge call check `QuotaService`. Optional Redis cache of query-embedding + judge result for recurring queries.
- **GDPR**: KB entries may contain PII → add to `DataRetentionService` policy.

## 3. Data model (migration V82)

```sql
-- V82__knowledge_base.sql
CREATE TABLE knowledge_bases (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    user_id          UUID NOT NULL,
    name             VARCHAR(120) NOT NULL,
    description      TEXT,
    parameter_set_id UUID NOT NULL REFERENCES parameter_sets(id),
    field_roles      JSONB NOT NULL DEFAULT '{}',   -- { "isim": {"embed":true,"keyword":true}, "kod": {"embed":false,"keyword":true} }
    unique_field     VARCHAR(100),                  -- nullable natural key for upsert re-import
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);
CREATE INDEX idx_kb_org ON knowledge_bases(organization_id) WHERE deleted_at IS NULL;

CREATE TABLE knowledge_base_entries (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    knowledge_base_id  UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    organization_id    UUID NOT NULL,
    data               JSONB NOT NULL,              -- { "kod":"4930", "isim":"Bürobedarf", "aciklama":"..." }
    embedding          vector(3072),                -- NO ANN index (seq scan; small KBs)
    search_text        TEXT,                        -- concatenated keyword-field values (dynamic → not a generated column)
    content_hash       VARCHAR(64),                 -- hash of embed-field text → skip re-embed on unchanged upsert
    embedding_dirty    BOOLEAN NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_kbe_kb    ON knowledge_base_entries(knowledge_base_id);
CREATE INDEX idx_kbe_dirty ON knowledge_base_entries(embedding_dirty) WHERE embedding_dirty = true;
-- Expression GIN index (keyword fields are dynamic per KB → cannot be a generated tsvector column):
CREATE INDEX idx_kbe_fts   ON knowledge_base_entries USING GIN (to_tsvector('simple', coalesce(search_text, '')));
```

- Uniqueness of `unique_field` enforced **in the service** (`WHERE kb_id = ? AND data->>field = ?`), not a DB constraint (the key column is dynamic per KB — avoid per-KB DDL).
- `vector(3072)` requires the pgvector extension (already enabled in V1).

## 4. Implementation phases

### Phase 0 — Marketplace snapshot foundation (prerequisite, same package)
Refactors publish/install from **live-reference** to **immutable snapshot**. Marketplace-wide change the KB feature rides on. **Heaviest / highest-risk phase** — touches working publish/install + its tests.
- **Snapshot storage = a serialized manifest, not snapshot-flagged rows.** Recommended: `marketplace_listing_snapshot(listing_id, version, manifest JSONB)` holding the automation nodes/edges/config + small referenced resources (categories, parameter sets, templates, webhook config, **KB schema + fieldRoles**). For `FULL` KBs, entries go in a side table `marketplace_listing_kb_snapshot_entry(listing_id, kb_ref, data JSONB, embedding vector(3072), search_tsv)` — JSONB can't hold thousands of 3072-float vectors. *(Alternative rejected: marking snapshot-owned rows in every entity table — confines blast radius vs. spreading a `snapshot` flag + query exclusion across ~9 tables.)*
- `publish`: discover referenced resources (reuse `MarketplaceResourceCloner.collectUuids`), serialize them + the flow into the manifest (+ KB entries to the side table per D13 policy). Bump `version`; re-publish regenerates.
- `install`: materialize the manifest into the buyer's org (reuse the cloner's config-rewrite/ID-remap + account-clearing logic, but reading from the manifest instead of the author's live entities). Source is the snapshot — **never** `findByIdAndUserId(automationId, authorId)`.
- **Retire** now-redundant guards: `AutomationServiceImpl` delete guard (`:238`) and any per-resource delete guard (kept only as soft UX warnings, optional). Author can delete freely; the snapshot is self-contained.
- Update `MarketplaceServiceTest`: publish builds snapshot; install reads snapshot; author deletion no longer breaks install.
- **Verify:** `./mvnw test` (marketplace suite green).

### Phase 1 — Backend foundation
- `V82__knowledge_base.sql` (above).
- Entities: `KnowledgeBase`, `KnowledgeBaseEntry` (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`, `@SQLRestriction("deleted_at IS NULL")` on KB; `embedding` mapped via the existing `VectorType` used by `Category`).
- Repositories (`OrgScopedRepository<KnowledgeBase>`, `KnowledgeBaseEntryRepository`):
  - `findClosestByEmbedding(kbId, queryVector, limit)` — native, `ORDER BY embedding <=> cast(:vec as vector) LIMIT :n` (mirror `CategoryRepository`), scoped `WHERE knowledge_base_id = :kbId AND embedding IS NOT NULL`.
  - `fullTextSearch(kbId, query, limit)` — `WHERE search_tsv @@ plainto_tsquery(:q) ORDER BY ts_rank(...) LIMIT :n`.
  - `findDirty(limit)` for the re-embed worker.
- **Verify:** `./mvnw compile`.

### Phase 2 — `KnowledgeBaseService` (CRUD + import)
- Interface + impl. DTOs as records: `KnowledgeBaseRequest`, `KnowledgeBaseResponse`, `KbEntryRequest`, `KbEntryResponse`, `KbImportRequest`.
- KB-config validation (NOT the automation validator): `KB_NO_EMBED_FIELD` (≥1 field must be `embed:true`), `field_roles` keys must exist in the referenced ParameterSet.
- Entry CRUD: on save, recompute `content_hash`, build embed-text (concat of `embed:true` fields) + `search_tsv` (keyword fields), set `embedding_dirty=true`.
- **CSV import** (`importEntries`): map CSV columns → ParameterSet fields; if `uniqueField` set → upsert by `data->>uniqueField` (skip re-embed when `content_hash` unchanged); else → full replace. All rows marked `embedding_dirty`.
- **Batch re-embed worker** (`@Scheduled` or triggered): pull `findDirty`, call `EmbeddingService` in batches, write `embedding`, clear flag. Off the request thread. Quota-checked.
- Controller `/api/v1/knowledge-bases`: `GET/POST /`, `GET/PUT/DELETE /{id}`, `GET/POST /{id}/entries`, `PUT/DELETE /{id}/entries/{eid}`, `POST /{id}/import`. Org threaded from `X-Org-Id` like marketplace.
- **Verify:** `./mvnw compile` + `KnowledgeBaseServiceTest`.

### Phase 3 — `KnowledgeBaseSearchService` (retrieve → judge → route)
- `search(kbId, queryText, topK, threshold, dryRun)` →
  1. Embed query (`EmbeddingService`). Empty KB / no candidates → `NO_MATCH` (no LLM call).
  2. Hybrid: vector top-N + full-text top-N → **RRF** merge → topK.
  3. **LLM judge** (`PromptService` template `kb-judge.txt`) — input = query + candidate records; output = structured `{ matchedId | null, confidence, reason }`, constrained to candidate IDs.
  4. Build `SearchOutcome { status (MATCHED/NOT_MATCHED/ERROR), matchedEntry, confidence, reason }` by comparing `confidence >= threshold`.
- New prompt file `prompts/kb-judge.txt` (+ PromptService docs row in CLAUDE.md table).
- **Verify:** `./mvnw compile` + `KnowledgeBaseSearchServiceTest` (mock EmbeddingService + Gemini).

### Phase 4 — `VECTOR_SEARCH` node
- `NodeType.VECTOR_SEARCH`. Config: `{ queryVariable, knowledgeBaseId, topK (1–10, def 5), confidenceThreshold }`.
- `VectorSearchNodeProcessor extends AbstractNodeProcessor` (mirror `CategorizeNodeProcessor`):
  - mock check first (`context.getMock`) → `processMock` (CATEGORIZE/INTEGRATION_CALL pattern).
  - resolve `queryVariable` via `VariableResolver` → `KnowledgeBaseSearchService.search(...)`.
  - inject `vectorsearch_<nodeId>.match.<field>` + `.confidence` + `.reason` (node-scoped, like `integration_<nodeId>.*`).
  - `byHandleWithContext(status, detail, Set.of(handle), ...)` per D9.
- `AutomationValidator` codes: `VECTOR_SEARCH_NO_KB`, `VECTOR_SEARCH_NO_QUERY` (both error). Hook into update/activate/publish like existing codes.
- `AutomationValidator` ports/cardinality: input + static `success`/`fail` handles.
- **Verify:** `./mvnw compile` + `AutomationValidatorTest` cases.

### Phase 5 — Test / Simulation wiring (the 10-item node checklist)
**Backend:**
- `resolveFieldValue` (`AutomationTestServiceImpl`): ensure `detail` shape (`match` nested map + flat `confidence`/`reason`) resolves for assertions.
- `TestModeServiceImpl.buildActionDescription`: `VECTOR_SEARCH` case (sim feed text).
- Dry-run `resultDetail` shape consistent with mock.

**Frontend:**
- `STATUS_BY_TYPE` (`automation-test-panel.component.ts:62`): `VECTOR_SEARCH: ['MATCHED','NOT_MATCHED','ERROR']`. (`STATUS_COLORS` already covers all three.)
- `formatNodeDetails` (`:539` switch): `case 'VECTOR_SEARCH'` → matched fields + `confidence` (`%`) + `reason` (mirror CATEGORIZE `:564`).
- `testPanelNodes` (`automation-editor.ts:169`): extract metadata = KB ParameterSet field names (`match.*`) + `confidence` + `reason` → assertion dropdowns.
- `AutomationLintService` (FE): mirror `VECTOR_SEARCH_NO_KB`/`VECTOR_SEARCH_NO_QUERY`.
- **Verify:** `npx ng build` + editor e2e.

### Phase 6 — Frontend KB feature + node config
- `models/knowledge-base.model.ts`; `core/services/knowledge-base.service.ts`.
- Feature `features/knowledge-base/` (or under dashboard): **list** (CrudPageBase) + **editor** (ParameterSet picker → field-role table [embed/keyword/unique toggles] → entry CRUD + **CSV upload** with column→field mapping). Route `/dashboard/knowledge-bases` (sidebar item; UI label "Knowledge Base" / DE "Wissensdatenbank" — the "context tab" the user asked for).
- `automation.model.ts`: `NODE_PALETTE` entry for `VECTOR_SEARCH` (icon/color), `NODE_DEFAULT_CONFIG`, `VectorSearchNodeConfig` (with `[key:string]: unknown` index sig).
- `node-config-panel`: VECTOR_SEARCH section — KB picker, `queryVariable` via `VariableComboboxComponent`, `topK`, `confidenceThreshold`. Ports: input + static `success`/`fail`.
- **Verify:** `npx ng build`.

### Phase 6b — KB in the marketplace snapshot (extends Phase 0; see §5)
- Migration `V83` (with Phase 0's snapshot tables): `marketplace_listings.kb_share_policy` JSONB; `knowledge_bases.hidden` + `locked` BOOLEAN (mirror `Automation.hidden`, default false); `marketplace_listing_kb_snapshot_entry(listing_id, kb_ref, data JSONB, embedding vector(3072), search_tsv)`.
- **Publish**: enumerate referenced KBs; per policy, freeze the KB shell into the manifest and (if `FULL`) entries into the snapshot side-table (embeddings verbatim). Persist `kb_share_policy` (default `SCHEMA_ONLY`); **warn on `FULL`** (PII/data export). PRIVATE listing forces `FULL`+hidden.
- **Install (materialize)**: recreate KB + its paramset under buyer ownership, remap `knowledgeBaseId` (cloner `rewriteConfig`); copy snapshot entries (`embedding_dirty=false`, no re-embed). Thread `hidden`/`locked`.
- KB get/entries API: refuse to return `data`/entries when `hidden=true` (same contract as the hidden automation flow API).
- Publish UI: per-referenced-KB toggle SCHEMA_ONLY/FULL + data-export warning on FULL.
- **Verify:** `MarketplaceServiceTest` (publish freezes KB shell / full entries; install materializes; hidden-on-private; author KB deletion does not affect an existing listing).

### Phase 7 — i18n, E2E, docs
- i18n (DE+EN): `kb_*` (feature), `auto_node_vector_search*`, `auto_lint_vector_search_*`, test labels (`%key%` placeholder format).
- E2E: `mocks/knowledge-base.mocks.ts`, `pages/knowledge-base.page.ts`, `tests/knowledge-base/*.spec.ts`; mock `VECTOR_SEARCH` `resultDetail` in editor tests.
- Update CLAUDE.md: PromptService table (`kb-judge.txt`), node checklist note, KB section. Update MEMORY roadmap.

## 5. Marketplace publish / install behavior

A `VECTOR_SEARCH` node references a `knowledgeBaseId`. Per **D14**, publish freezes the automation
+ its referenced resources into an **immutable snapshot**, and install materializes the buyer's copy
from that snapshot (not the author's live data). The KB joins the snapshot. The new question a KB
raises (beyond categories/templates) is **whether the entries — the actual data — are frozen into the
snapshot.** That choice is made by the publisher at publish time (D13) and decided once; later author
edits/deletes never touch an existing listing.

### Clone matrix

| Listing | Share mode | Result for the buyer |
|---------|-----------|----------------------|
| PUBLIC  | `SCHEMA_ONLY` (default) | Editable **empty** KB (correct schema + field roles); buyer fills / CSV-imports own data. For proprietary / buyer-specific data. |
| PUBLIC  | `FULL` | Editable KB **with all entries** (embeddings copied verbatim, no re-embed cost). For data-as-product (e.g. an SKR 03 mapping). |
| PRIVATE | (forced `FULL` + hidden) | KB **with entries but opaque** — `hidden`/`locked`, entries API refuses to return content. Automation runs; buyer can't read the data. |

### Snapshot vs visibility are orthogonal
The snapshot is taken **identically for PUBLIC and PRIVATE** — it is always full-fidelity (the server must
hold everything needed to materialize a working copy) and visibility-independent. Visibility only changes
the **flags on the materialized buyer copy** and what the detail surface exposes:

| Listing | Materialized copy | Detail surface reads from snapshot |
|---------|-------------------|-------------------------------------|
| **PUBLIC** | `hidden=false`, editable — buyer opens it in the normal editor (white-box). KB visible/editable (empty if `SCHEMA_ONLY`, with data if `FULL`). | Full flow **preview** rendered from the snapshot manifest. |
| **PRIVATE** | `hidden=true`, `locked=true` — runs but the get/flow API refuses nodes/edges/constant values; only publishable constants editable via Configure (black-box). KB forced `FULL`+`hidden` (runs, opaque). | Only I/O contract + description; flow content gated, **never** returned. |

The snapshot blob/side-tables are **server-internal** and never exposed to buyers directly. Hidden enforcement
lives on the *materialized* copy (`Automation.hidden`, `KnowledgeBase.hidden`), not on the snapshot.

### Mechanics
1. **At publish** — discover refs (`collectUuids` finds `knowledgeBaseId`), freeze the KB shell into the manifest + its `parameterSetId`; if policy=`FULL`, copy entries (`data`, `embedding` **verbatim**, `search_tsv`, `content_hash`) into the KB snapshot side-table.
2. **At install** — materialize from the snapshot: recreate KB + paramset under buyer ownership, remap `knowledgeBaseId` in node config (cloner's `rewriteConfig` ID-remap), clear `accountIds`.
3. **Embeddings travel free** — `FULL` keeps `embedding` verbatim (same model/dim per D5), as `cloneCategory` already does → **no buyer-quota re-embed**, at publish *or* install.
4. **Hidden contract** — `KnowledgeBase.hidden`/`locked`; when `hidden=true` the KB get/entries API returns no `data` (mirrors the `Automation.hidden` rule). PRIVATE listing ⇒ materialized KB is hidden.
5. **Policy storage** — `marketplace_listings.kb_share_policy` JSONB (`{ "<kbId>": "SCHEMA_ONLY"|"FULL" }`), declared at publish; governs what enters the snapshot.
6. **GDPR** — `FULL` = exporting your data into the snapshot for buyers; publish UI warns + prompts a PII acknowledgement.
7. **Integrations** — same path. An automation calling an integration via `INTEGRATION_CALL` is *already* publish-blocked (no recursive clone yet), so a KB reached that way is gated until recursive integration snapshotting lands; a directly-published INTEGRATION containing `VECTOR_SEARCH` is covered here.

## 6. Variable contract (downstream of the node)

```
vectorsearch_<nodeId>.match.<paramsetField>   # e.g. .match.kod = "4930", .match.isim = "Bürobedarf"
vectorsearch_<nodeId>.confidence              # 0–100
vectorsearch_<nodeId>.reason                  # judge's short rationale
```
On the `fail` handle these are absent/empty; `status` (MATCHED/NOT_MATCHED/ERROR) distinguishes the cause in traces.

## 7. Out of scope (deferred)
- Full agentic **AI Agent** node (tool-calling loop) — the same `KnowledgeBaseSearchService` becomes one of its tools.
- ANN index / dimension reduction (`halfvec(3072)` or `vector(1536)`) — only if a single KB grows to tens of thousands of rows.
- Cross-KB / multi-KB search in one node.
- **Version history** — v1 keeps one current snapshot per listing (re-publish regenerates + bumps `version`); retaining multiple historical snapshot versions / letting buyers pin a version is deferred.
- Recursive integration cloning/snapshotting — `INTEGRATION_CALL`-containing automations stay publish-blocked, so KBs reached only via an integration call are gated until that lands.
