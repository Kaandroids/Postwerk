/**
 * Knowledge base models — org-scoped reference data searched by the VECTOR_SEARCH automation node.
 * Mirrors the backend DTOs in {@code com.postwerk.dto} (see doc/KNOWLEDGE_BASE_DESIGN.md).
 */

/** Per-field retrieval roles: whether a field feeds the semantic embedding and/or the keyword index. */
export interface KbFieldRole {
  embed: boolean;
  keyword: boolean;
}

/** A knowledge base: a parameter-set schema + a per-field embed/keyword overlay + its entries. */
export interface KnowledgeBase {
  id: string;
  name: string;
  description?: string;
  parameterSetId: string;
  fieldRoles: Record<string, KbFieldRole>;
  uniqueField?: string | null;
  entryCount: number;
  locked: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Create/update payload for a knowledge base. */
export interface KnowledgeBaseRequest {
  name: string;
  description?: string;
  parameterSetId: string;
  fieldRoles: Record<string, KbFieldRole>;
  uniqueField?: string | null;
}

/** A single filled entry (field values keyed by parameter-set field name). Never carries the embedding. */
export interface KbEntry {
  id: string;
  data: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

/** Create/update payload for a single entry. */
export interface KbEntryRequest {
  data: Record<string, unknown>;
}
