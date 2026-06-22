/**
 * Pure helpers for parsing and building the 5-field cron expressions used by
 * schedule-trigger nodes (`minute hour day-of-month month day-of-week`).
 *
 * <p>Extracted verbatim from {@code AutomationEditorComponent} to isolate the
 * stateless cron string math from the editor's UI/state concerns. Behaviour is
 * unchanged from the original inline helpers.</p>
 */

/** Reads "HH:MM" from a cron's minute/hour fields, defaulting to 09:00. */
export function parseCronTime(cron?: string): string {
  if (!cron) return '09:00';
  const parts = cron.split(/\s+/);
  if (parts.length < 2) return '09:00';
  const m = parseInt(parts[0], 10) || 0;
  const h = parseInt(parts[1], 10) || 9;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/** Reads the day-of-week field (index 4), defaulting to 1. */
export function parseCronDay(cron?: string): number {
  if (!cron) return 1;
  const parts = cron.split(/\s+/);
  if (parts.length < 5) return 1;
  return parseInt(parts[4], 10) || 1;
}

/** Reads the day-of-month field (index 2), defaulting to 1. */
export function parseCronDayOfMonth(cron?: string): number {
  if (!cron) return 1;
  const parts = cron.split(/\s+/);
  if (parts.length < 4) return 1;
  return parseInt(parts[2], 10) || 1;
}

/** Splits "HH:MM" into numeric [hour, minute]. */
export function parseTimeParts(time: string): [number, number] {
  const [h, m] = time.split(':').map(Number);
  return [h, m];
}

/** Builds a daily cron (`m h * * *`). */
export function buildDailyCron(h: number, m: number): string {
  return `${m} ${h} * * *`;
}

/** Builds a weekly cron (`m h * * day`). */
export function buildWeeklyCron(h: number, m: number, day: number): string {
  return `${m} ${h} * * ${day}`;
}

/** Builds a monthly cron (`m h dom * *`). */
export function buildMonthlyCron(h: number, m: number, dayOfMonth: number): string {
  return `${m} ${h} ${dayOfMonth} * *`;
}
