import { I18nService } from '../app/core/services/i18n.service';

/**
 * A no-op {@link I18nService} whose {@code t()} echoes the key back (and interpolates {@code %name%}
 * placeholders from the params map). Lets specs assert against stable keys instead of translated
 * copy, and avoids pulling the 7k-line dictionary into every test module.
 */
export function stubI18n(): I18nService {
  return {
    t: (key: string, params?: Record<string, string>) =>
      params
        ? Object.entries(params).reduce((s, [k, val]) => s.replace(`%${k}%`, val), key)
        : key,
  } as unknown as I18nService;
}

/** Provider entry wiring the {@link stubI18n} echo translator in place of the real service. */
export function provideStubI18n() {
  return { provide: I18nService, useValue: stubI18n() };
}
