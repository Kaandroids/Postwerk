import { humanizeError } from './error.util';

describe('humanizeError', () => {
  it('extracts the backend error message', () => {
    expect(humanizeError({ error: { message: 'Boom' } }, 'fallback')).toBe('Boom');
  });

  it('falls back when the error has no message', () => {
    expect(humanizeError({ error: {} }, 'fallback')).toBe('fallback');
  });

  it('falls back when there is no error envelope', () => {
    expect(humanizeError({}, 'fallback')).toBe('fallback');
  });

  it('falls back for null/undefined', () => {
    expect(humanizeError(null, 'fallback')).toBe('fallback');
    expect(humanizeError(undefined, 'fallback')).toBe('fallback');
  });

  it('falls back when the message is empty', () => {
    expect(humanizeError({ error: { message: '' } }, 'fallback')).toBe('fallback');
  });
});
