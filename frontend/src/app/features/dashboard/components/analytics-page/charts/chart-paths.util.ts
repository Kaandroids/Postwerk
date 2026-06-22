/**
 * Pure SVG geometry helpers for the analytics charts — line/area paths and donut arcs.
 * Ported from the design handoff; framework-agnostic. Paths are computed against a fixed
 * viewBox and stretched with `preserveAspectRatio="none"`, so no width measurement is needed
 * (strokes stay crisp via `vector-effect="non-scaling-stroke"`).
 */

/** SVG line path for `values` over a [0..w]×[0..h] box (optional padding + fixed max). */
export function linePath(values: number[], w: number, h: number, pad = 0, max: number | null = null): string {
  const n = values.length;
  if (!n) return '';
  const hi = max != null ? max : Math.max(...values, 1);
  const x = (i: number) => pad + (n === 1 ? 0 : (i / (n - 1)) * (w - pad * 2));
  const y = (v: number) => pad + (1 - v / hi) * (h - pad * 2);
  return values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(2)} ${y(v).toFixed(2)}`).join(' ');
}

/** Close a line path into a filled area down to the baseline. */
export function areaPath(values: number[], w: number, h: number, pad = 0, max: number | null = null): string {
  const n = values.length;
  if (!n) return '';
  const line = linePath(values, w, h, pad, max);
  const x = (i: number) => pad + (n === 1 ? 0 : (i / (n - 1)) * (w - pad * 2));
  return `${line} L${x(n - 1).toFixed(2)} ${h} L${x(0).toFixed(2)} ${h} Z`;
}

export interface DonutArc { dash: number; off: number; circ: number; }

/** Donut arc segments (dash length + offset) from a set of values. */
export function donutArcs(values: number[], radius: number): DonutArc[] {
  const total = values.reduce((a, b) => a + b, 0) || 1;
  const circ = 2 * Math.PI * radius;
  let offset = 0;
  return values.map((v) => {
    const frac = v / total;
    const seg: DonutArc = { dash: frac * circ, off: offset, circ };
    offset += frac * circ;
    return seg;
  });
}

let gradSeq = 0;
/** Unique gradient id (SVG defs must be unique per instance in the DOM). */
export function nextGradId(prefix = 'an-grad'): string {
  return `${prefix}-${++gradSeq}`;
}
