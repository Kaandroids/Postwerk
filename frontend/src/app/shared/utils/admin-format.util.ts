/**
 * Shared presentation helpers for the admin console. Centralizes small functions that were
 * duplicated across the admin feature components (avatar tint, name initial, plan badge tone)
 * so siblings stay visually consistent.
 */

/** Deterministic 0–359 hue derived from a seed string, used for avatar background tints. */
export function avatarHue(seed: string | null | undefined): number {
  const s = seed ?? '';
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
  return h;
}

/** First character of a name, upper-cased; {@code "?"} when empty/blank. */
export function initial(name: string | null | undefined): string {
  return (name?.trim()?.[0] ?? '?').toUpperCase();
}

/** Canonical badge tone for a plan name — one source of truth so every admin page agrees. */
export function planTone(name: string | null | undefined): string {
  switch ((name ?? '').toUpperCase()) {
    case 'FREE':
    case 'STARTER': return 'slate';
    case 'PRO': return 'violet';
    case 'BUSINESS': return 'green';
    case 'ENTERPRISE': return 'plum';
    default: return name ? 'violet' : 'slate';
  }
}
