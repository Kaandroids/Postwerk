/**
 * Extracts a human-readable message from an HTTP error response, falling back to
 * the provided default. Centralizes the `err?.error?.message` access used by inline
 * error handlers across the app.
 */
export function humanizeError(err: unknown, fallback: string): string {
  return (err as { error?: { message?: string } } | null)?.error?.message || fallback;
}
