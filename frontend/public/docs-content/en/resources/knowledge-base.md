# Knowledge bases

_A knowledge base is an org-scoped table of reference entries the Knowledge match node searches — map free text onto a fixed list like account codes, SKUs, or routing rules._

## Structure

A KB reuses a **parameter set** as its schema, so its columns are the same `{name, type, description}` fields used elsewhere. Each entry is one filled row, stored alongside a semantic embedding and full-text search text.

## Field roles

| Role | Effect |
|------|--------|
| Embed | Field is included in the semantic vector |
| Keyword | Field is included in full-text search |
| Unique key | Optional natural key for re-import upsert |

## Importing entries

Add rows inline, or import a CSV. With a unique key set, re-importing upserts and only re-embeds changed rows; without one, it replaces the set. Embedding happens asynchronously off the request thread, so large imports stay responsive.

## Searching from a flow

Point a [Knowledge match](/docs/nodes/vector_search) node at the KB. It retrieves the closest entries with hybrid vector + keyword search (fused by reciprocal rank), a model judges the best one, and the run routes by confidence. A confident match leaves through `success` and injects `{{vectorsearch_<id>.match.<field>}}`, `{{vectorsearch_<id>.confidence}}`, and `{{vectorsearch_<id>.reason}}`; a below-threshold result or error leaves through `fail`.

> [!INFO]
> Knowledge bases are never shared across organizations. When you publish to the Marketplace you choose whether entries travel with the automation — see [Publishing & installing](/docs/marketplace/publishing).
