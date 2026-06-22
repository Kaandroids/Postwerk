import { NodeType } from '../../models/automation.model';

/**
 * Bilingual string. The docs surface and the in-editor node-info modal both
 * render NodeDoc content, picking `de` / `en` via {@link pick} from the
 * active {@code I18nService.lang()}.
 */
export interface Bi {
  de: string;
  en: string;
}

/** Resolves a bilingual value for the active language. */
export function pick<T>(bi: { de: T; en: T }, lang: 'de' | 'en'): T {
  return bi[lang];
}

/** A single configuration field of a node. */
export interface NodeDocField {
  /** Config key / UI field name, e.g. `triggerMode`, `confidenceThreshold`. */
  name: string;
  /** Short type/shape hint, e.g. `enum`, `string[]`, `0–100`, `expression`. */
  type: string;
  desc: Bi;
}

/** A `{{variable}}` a node injects for downstream nodes. */
export interface NodeDocVar {
  /** Variable path WITHOUT braces, e.g. `http_<id>.statusCode`. */
  v: string;
  d: Bi;
}

/**
 * Handle data type — drives the dot color + shape in the node-reference
 * template (mirrors the editor's connector shapes). `email`/`any` render as a
 * ring (hollow); `cat`/`param`/`json`/`done` render filled.
 */
export type HandleType = 'email' | 'any' | 'cat' | 'param' | 'json' | 'done';

/** An input or output handle of a node. */
export interface NodeDocHandle {
  /** Handle label as shown on the canvas, e.g. `new-email`, `fallback`, `success`. */
  name: string;
  htype: HandleType;
  desc: Bi;
}

export interface NodeDocExample {
  title: Bi;
  body: Bi;
  /** Optional fenced code sample (rendered verbatim, not localized). */
  code?: { lang: string; body: string };
}

/** A distinct operating mode of a multi-tab node (e.g. Trigger: Email/Webhook/Schedule). */
export interface NodeDocMode {
  name: Bi;
  desc: Bi;
  /** Variables this specific mode produces (modes can differ). */
  produces?: NodeDocVar[];
  /** Output handles this specific mode exposes. */
  handlesOut?: NodeDocHandle[];
}

/**
 * Full reference for one automation node type. Color, icon and display label
 * are NOT stored here — they are derived from `NODE_PALETTE`
 * (`getNodeColor`/`getNodeIcon`/`getNodeLabelKey`) so there is a single source
 * of truth shared with the editor.
 */
export interface NodeDoc {
  type: NodeType;
  summary: Bi;
  /** Operating modes for multi-tab nodes; rendered as a dedicated section. */
  modes?: NodeDocMode[];
  fields: NodeDocField[];
  produces: NodeDocVar[];
  handlesIn: NodeDocHandle[];
  handlesOut: NodeDocHandle[];
  example?: NodeDocExample;
  /** Extra callout-worthy facts: mockability, supervised mode, validation, guards. */
  notes?: Bi[];
}
