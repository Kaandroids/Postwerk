import { afterEach, beforeEach, vi } from 'vitest';
import { relativeTime } from './relative-time.util';

const NOW = new Date('2026-06-25T12:00:00Z');
const ago = (ms: number) => new Date(NOW.getTime() - ms).toISOString();
const MIN = 60_000;
const HOUR = 3_600_000;
const DAY = 86_400_000;

describe('relativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('labels sub-minute differences as "just now"', () => {
    expect(relativeTime(ago(30_000), false)).toBe('just now');
    expect(relativeTime(ago(30_000), true)).toBe('gerade eben');
  });

  it('labels minutes and hours', () => {
    expect(relativeTime(ago(5 * MIN), false)).toBe('5m ago');
    expect(relativeTime(ago(5 * MIN), true)).toBe('vor 5 Min');
    expect(relativeTime(ago(3 * HOUR), false)).toBe('3h ago');
    expect(relativeTime(ago(3 * HOUR), true)).toBe('vor 3 Std');
  });

  it('labels yesterday and recent days', () => {
    expect(relativeTime(ago(DAY), false)).toBe('yesterday');
    expect(relativeTime(ago(DAY), true)).toBe('gestern');
    expect(relativeTime(ago(3 * DAY), false)).toBe('3d ago');
    expect(relativeTime(ago(3 * DAY), true)).toBe('vor 3 Tagen');
  });

  it('falls back to a short localized date for older entries', () => {
    const old = ago(30 * DAY);
    expect(relativeTime(old, false)).toMatch(/[A-Za-z]{3}\s\d+/);
    // German locale uses a different month abbreviation, just assert it is non-empty.
    expect(relativeTime(old, true).length).toBeGreaterThan(0);
  });
});
