import { avatarHue, initial, planTone } from './admin-format.util';

describe('avatarHue', () => {
  it('is deterministic for the same seed', () => {
    expect(avatarHue('alice')).toBe(avatarHue('alice'));
  });

  it('stays within the 0–359 hue range', () => {
    for (const seed of ['', 'a', 'a very long organization name with spaces', 'ÜÖÄ']) {
      const h = avatarHue(seed);
      expect(h).toBeGreaterThanOrEqual(0);
      expect(h).toBeLessThan(360);
    }
  });

  it('treats null/undefined as the empty seed (hue 0)', () => {
    expect(avatarHue(null)).toBe(0);
    expect(avatarHue(undefined)).toBe(0);
    expect(avatarHue('')).toBe(0);
  });
});

describe('initial', () => {
  it('returns the upper-cased first character', () => {
    expect(initial('alice')).toBe('A');
    expect(initial('bob')).toBe('B');
  });

  it('ignores leading whitespace', () => {
    expect(initial('  zed')).toBe('Z');
  });

  it('falls back to "?" for empty/blank/nullish names', () => {
    expect(initial('')).toBe('?');
    expect(initial('   ')).toBe('?');
    expect(initial(null)).toBe('?');
    expect(initial(undefined)).toBe('?');
  });
});

describe('planTone', () => {
  it('maps known plans case-insensitively', () => {
    expect(planTone('free')).toBe('slate');
    expect(planTone('STARTER')).toBe('slate');
    expect(planTone('Pro')).toBe('violet');
    expect(planTone('business')).toBe('green');
    expect(planTone('ENTERPRISE')).toBe('plum');
  });

  it('defaults to violet for an unknown non-empty plan', () => {
    expect(planTone('custom-tier')).toBe('violet');
  });

  it('defaults to slate when there is no plan', () => {
    expect(planTone('')).toBe('slate');
    expect(planTone(null)).toBe('slate');
    expect(planTone(undefined)).toBe('slate');
  });
});
