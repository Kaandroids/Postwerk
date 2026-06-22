/**
 * Accessibility (a11y) proof scan — runs axe-core (WCAG 2.0/2.1 A + AA) against every
 * dashboard surface and writes an impact-categorised report to test-proof/a11y-report.md.
 * Run: npx playwright test tests/proof/a11y.spec.ts
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { applyProofMocks, DASHBOARD_SURFACES } from '../../fixtures/proof-mocks';
import AxeBuilder from '@axe-core/playwright';
import * as fs from 'fs';
import * as path from 'path';

const OUT = path.join(__dirname, '../../../../test-proof/a11y-report.md');

test('a11y scan of all dashboard surfaces', async ({ authenticatedPage: page }) => {
  test.setTimeout(180_000);
  const api = new MockApi();
  applyProofMocks(api);
  await api.apply(page);

  const rows: string[] = [];
  const totals = { critical: 0, serious: 0, moderate: 0, minor: 0 };
  const ruleHits = new Map<string, number>();

  for (const s of DASHBOARD_SURFACES) {
    await page.goto(s.path);
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(400);

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    const by = { critical: 0, serious: 0, moderate: 0, minor: 0 };
    for (const v of results.violations) {
      const impact = (v.impact ?? 'minor') as keyof typeof by;
      by[impact] += v.nodes.length;
      ruleHits.set(v.id, (ruleHits.get(v.id) ?? 0) + v.nodes.length);
    }
    (Object.keys(totals) as (keyof typeof totals)[]).forEach((k) => (totals[k] += by[k]));
    rows.push(`| ${s.name} | ${by.critical} | ${by.serious} | ${by.moderate} | ${by.minor} |`);
  }

  const topRules = [...ruleHits.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12)
    .map(([id, n]) => `- \`${id}\` — ${n} nodes`);

  const md = [
    '# Postwerk — Accessibility (a11y) Scan',
    '',
    `> axe-core, WCAG 2.0/2.1 **A + AA** · ${DASHBOARD_SURFACES.length} dashboard surfaces · chromium · (auto-generated)`,
    '',
    '## Violations (node count) — by impact',
    '',
    '| Surface | Critical | Serious | Moderate | Minor |',
    '|---|---|---|---|---|',
    ...rows,
    `| **TOTAL** | **${totals.critical}** | **${totals.serious}** | **${totals.moderate}** | **${totals.minor}** |`,
    '',
    '## Most frequently violated rules',
    '',
    ...topRules,
    '',
  ].join('\n');
  fs.writeFileSync(OUT, md);

  // Report-first: the scan completing over every surface is the assertion here; the
  // impact breakdown is the proof artifact. Critical-gate tightening is a follow-up.
  expect(rows.length).toBe(DASHBOARD_SURFACES.length);
});
