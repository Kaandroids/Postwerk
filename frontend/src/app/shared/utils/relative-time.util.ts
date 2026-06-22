/**
 * Formats an ISO date string as a relative time label (e.g. "5m ago" / "vor 5 Min"),
 * falling back to a short localized date for older entries.
 * Shared by the email list rows and the expanded-email detail pane.
 */
export function relativeTime(dateStr: string, isDE: boolean): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHr = Math.floor(diffMs / 3600000);
  const diffDay = Math.floor(diffMs / 86400000);

  if (diffMin < 1) return isDE ? 'gerade eben' : 'just now';
  if (diffMin < 60) return isDE ? `vor ${diffMin} Min` : `${diffMin}m ago`;
  if (diffHr < 24) return isDE ? `vor ${diffHr} Std` : `${diffHr}h ago`;
  if (diffDay === 1) return isDE ? 'gestern' : 'yesterday';
  if (diffDay < 7) return isDE ? `vor ${diffDay} Tagen` : `${diffDay}d ago`;
  return date.toLocaleDateString(isDE ? 'de-DE' : 'en-US', { month: 'short', day: 'numeric' });
}
