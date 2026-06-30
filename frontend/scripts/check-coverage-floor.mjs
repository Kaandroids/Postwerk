#!/usr/bin/env node
/**
 * Ratcheting frontend coverage gate (the analogue of the backend's `jacoco-check`).
 *
 * Reads the line coverage from the Vitest lcov report and fails if it dropped below the floor
 * committed in `frontend/coverage-floor.json`. The floor only ever moves UP: each coverage PR
 * raises it, so coverage can never silently regress. Run after `ng test --no-watch`.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const lcovPath = join(root, 'coverage', 'frontend', 'lcov.info');
const floorPath = join(root, 'coverage-floor.json');

let lcov;
try {
  lcov = readFileSync(lcovPath, 'utf8');
} catch {
  console.error(`✗ coverage report not found at ${lcovPath} — run \`ng test --no-watch\` first.`);
  process.exit(1);
}

let found = 0;
let hit = 0;
for (const line of lcov.split('\n')) {
  if (line.startsWith('LF:')) found += Number(line.slice(3));
  else if (line.startsWith('LH:')) hit += Number(line.slice(3));
}
const pct = found ? (100 * hit) / found : 0;
const floor = JSON.parse(readFileSync(floorPath, 'utf8')).lines;
const pctStr = pct.toFixed(2);

if (pct + 1e-9 < floor) {
  console.error(`✗ Frontend line coverage ${pctStr}% (${hit}/${found}) is below the floor of ${floor}%.`);
  console.error('  Add tests, or — only if a drop is truly justified — lower "lines" in frontend/coverage-floor.json.');
  process.exit(1);
}

console.log(`✓ Frontend line coverage ${pctStr}% (${hit}/${found}) meets the floor of ${floor}%.`);
if (pct - floor >= 2) {
  console.log(`  ↑ ${(pct - floor).toFixed(2)} pts above the floor — bump "lines" in frontend/coverage-floor.json to lock it in.`);
}
