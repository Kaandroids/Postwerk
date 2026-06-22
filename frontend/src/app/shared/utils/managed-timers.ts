import { DestroyRef, inject } from '@angular/core';

/**
 * A small timeout registry tied to the current injection context's {@link DestroyRef}: every timer
 * created via {@link ManagedTimers.set} is automatically cleared when the component/directive is
 * destroyed, so debounce/flash/refresh callbacks never fire on a destroyed view. Call once as a
 * field initializer (injection context): `private timers = managedTimers();`
 */
export interface ManagedTimers {
  /** Schedules a timeout that is auto-cleared on destroy; returns the handle. */
  set(fn: () => void, ms: number): ReturnType<typeof setTimeout>;
  /** Clears a previously-set timeout (no-op for null). */
  clear(id: ReturnType<typeof setTimeout> | null): void;
}

export function managedTimers(): ManagedTimers {
  const ids = new Set<ReturnType<typeof setTimeout>>();
  inject(DestroyRef).onDestroy(() => {
    ids.forEach(clearTimeout);
    ids.clear();
  });
  return {
    set(fn, ms) {
      const id = setTimeout(() => {
        ids.delete(id);
        fn();
      }, ms);
      ids.add(id);
      return id;
    },
    clear(id) {
      if (id !== null) {
        clearTimeout(id);
        ids.delete(id);
      }
    },
  };
}
