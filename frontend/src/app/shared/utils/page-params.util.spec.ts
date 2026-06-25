import { pageParams } from './page-params.util';

describe('pageParams', () => {
  it('always sets page and size', () => {
    const params = pageParams({}, 2, 50);
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('50');
  });

  it('appends defined, non-empty filters by their key', () => {
    const params = pageParams({ status: 'ACTIVE', q: 'acme' }, 0, 20);
    expect(params.get('status')).toBe('ACTIVE');
    expect(params.get('q')).toBe('acme');
  });

  it('skips undefined, null and empty-string filters', () => {
    const params = pageParams({ status: undefined, q: null, role: '' }, 0, 20);
    expect(params.has('status')).toBe(false);
    expect(params.has('q')).toBe(false);
    expect(params.has('role')).toBe(false);
  });

  it('keeps falsy-but-meaningful values (0, false)', () => {
    const params = pageParams({ minSeats: 0, archived: false }, 0, 20);
    expect(params.get('minSeats')).toBe('0');
    expect(params.get('archived')).toBe('false');
  });
});
