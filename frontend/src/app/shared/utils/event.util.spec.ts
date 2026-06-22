import { v } from './event.util';

describe('v (event value extractor)', () => {
  it('returns the value of the event target', () => {
    const event = { target: { value: 'hello' } } as unknown as Event;
    expect(v(event)).toBe('hello');
  });

  it('returns an empty string for an empty input', () => {
    const event = { target: { value: '' } } as unknown as Event;
    expect(v(event)).toBe('');
  });
});
