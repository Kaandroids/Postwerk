import { TestBed } from '@angular/core/testing';
import { I18nService } from './i18n.service';

/**
 * Cross-stack contract guard for the AI-chat phase buttons.
 *
 * The confirm/cancel buttons send their i18n *copy* as a chat message; the backend
 * {@code ConversationPhaseManager} then drives PLANNING → BUILDING / → OPEN by regex-matching that
 * text. The two ends live in different languages and can drift apart silently — which is exactly how
 * the cancel button got stuck (its copy "Nein, abbrechen." never matched the anchored pattern).
 *
 * These tests mirror the backend patterns verbatim and assert the real DE+EN button copy satisfies
 * them, so changing either side without the other fails fast here.
 */
describe('AI chat phase-button copy ↔ ConversationPhaseManager contract', () => {
  // --- Mirror of ConversationPhaseManager patterns (keep in sync with the Java source) -----------
  const CONFIRMATION = /^(ja|yes|ok|passt|mach|do it|build it|genau|los|perfekt|gut so|sieht gut aus|go ahead|lgtm|jap|klar|yep|yup|sure|bitte|mach das|let's go)[.!,\s]*$/i;
  const SKIP_PLANNING = /.*(just do it|build it|mach einfach|einfach machen|don't ask|frag nicht|erstell es|create it|bau es|direkt bauen|ohne fragen|ohne nachfragen).*/i;
  const CANCELLATION = /^(nein|no|abbrechen|cancel|nevermind|never mind|lass mal|doch nicht|stop|halt|nicht|nö|nope)([.!,\s]+(abbrechen|cancel|stop|verwerfen|discard|lass es))?[.!,\s]*$/i;

  // PLANNING branch accepts confirmation as (CONFIRMATION || SKIP_PLANNING).
  const isConfirm = (s: string) => CONFIRMATION.test(s.trim()) || SKIP_PLANNING.test(s.trim());
  const isCancel = (s: string) => CANCELLATION.test(s.trim());

  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
  });

  for (const lang of ['de', 'en'] as const) {
    it(`[${lang}] confirm-button copy is recognised as a confirmation`, () => {
      i18n.setLang(lang);
      const copy = i18n.t('chat_confirm_plan');

      expect(copy.length).toBeGreaterThan(0);
      expect(isConfirm(copy)).toBe(true);
      // A confirmation must not also read as a cancellation.
      expect(isCancel(copy)).toBe(false);
    });

    it(`[${lang}] cancel-button copy is recognised as a cancellation`, () => {
      i18n.setLang(lang);
      const copy = i18n.t('chat_cancel_plan');

      expect(copy.length).toBeGreaterThan(0);
      expect(isCancel(copy)).toBe(true);
      // A cancellation must not also read as a confirmation.
      expect(isConfirm(copy)).toBe(false);
    });
  }

  // Regression: the exact strings that previously slipped through the anchored pattern.
  it('regression — "Nein, abbrechen." / "No, cancel." are cancellations', () => {
    expect(isCancel('Nein, abbrechen.')).toBe(true);
    expect(isCancel('No, cancel.')).toBe(true);
  });

  it('does not treat a planning sentence containing "abbrechen" as a cancellation', () => {
    expect(isCancel('Baue eine Automation die eine Bestellung abbrechen kann')).toBe(false);
  });
});
