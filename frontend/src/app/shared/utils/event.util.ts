/**
 * Extracts the value from an input/select/textarea event target.
 * Shorthand for template bindings: `(input)="field.set(v($event))"`.
 */
export function v(e: Event): string {
  return (e.target as HTMLInputElement).value;
}
