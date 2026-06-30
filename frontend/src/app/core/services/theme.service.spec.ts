import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => localStorage.clear());

  function build() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    return TestBed.inject(ThemeService);
  }

  it('defaults to light / warm when nothing is stored', () => {
    const s = build();
    expect(s.theme()).toBe('light');
    expect(s.darkVariant()).toBe('warm');
  });

  it('restores a valid stored theme + variant on construction', () => {
    localStorage.setItem('pw-theme', 'dark');
    localStorage.setItem('pw-dark-variant', 'plum');
    const s = build();
    expect(s.theme()).toBe('dark');
    expect(s.darkVariant()).toBe('plum');
  });

  it('ignores invalid stored values, falling back to the defaults', () => {
    localStorage.setItem('pw-theme', 'neon');
    localStorage.setItem('pw-dark-variant', 'rainbow');
    const s = build();
    expect(s.theme()).toBe('light');
    expect(s.darkVariant()).toBe('warm');
  });

  it('toggle() flips between light and dark', () => {
    const s = build();
    s.toggle();
    expect(s.theme()).toBe('dark');
    s.toggle();
    expect(s.theme()).toBe('light');
  });

  it('setVariant() updates the dark variant', () => {
    const s = build();
    s.setVariant('black');
    expect(s.darkVariant()).toBe('black');
  });
});
