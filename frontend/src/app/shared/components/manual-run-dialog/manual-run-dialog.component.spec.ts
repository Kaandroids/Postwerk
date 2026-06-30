import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ManualRunDialogComponent } from './manual-run-dialog.component';
import { AutomationService } from '../../../core/services/automation.service';
import { ParameterSetService } from '../../../core/services/parameter-set.service';
import { provideStubI18n } from '../../../../testing';

interface Access {
  buildPayload(): Record<string, unknown>;
  load(id: string): void;
}

function param(name: string, type: string, extra: Record<string, unknown> = {}) {
  return { name, type, required: false, isList: false, ...extra } as never;
}

describe('ManualRunDialogComponent', () => {
  let automation: { get: ReturnType<typeof vi.fn>; runManually: ReturnType<typeof vi.fn> };
  let paramSets: { get: ReturnType<typeof vi.fn> };
  let cmp: ManualRunDialogComponent;
  let acc: Access;

  beforeEach(() => {
    automation = { get: vi.fn(() => of({ nodes: [] })), runManually: vi.fn(() => of(undefined)) };
    paramSets = { get: vi.fn(() => of({ parameters: [] })) };
    TestBed.configureTestingModule({
      imports: [ManualRunDialogComponent],
      providers: [
        provideStubI18n(),
        { provide: AutomationService, useValue: automation },
        { provide: ParameterSetService, useValue: paramSets },
      ],
    });
    const fixture = TestBed.createComponent(ManualRunDialogComponent);
    fixture.componentRef.setInput('automationId', 'a1');
    fixture.componentRef.setInput('automationName', 'My flow');
    cmp = fixture.componentInstance;
    acc = cmp as unknown as Access;
  });

  it('setValue / onInput / onCheckbox write into the values map', () => {
    cmp.setValue('x', 'hello');
    expect(cmp.values()['x']).toBe('hello');
    cmp.onInput('x', { target: { value: 'typed' } } as unknown as Event);
    expect(cmp.values()['x']).toBe('typed');
    cmp.onCheckbox('flag', { target: { checked: true } } as unknown as Event);
    expect(cmp.values()['flag']).toBe('true');
    cmp.onCheckbox('flag', { target: { checked: false } } as unknown as Event);
    expect(cmp.values()['flag']).toBe('false');
  });

  it('isMissing flags only empty required non-boolean fields', () => {
    cmp.values.set({ a: '' });
    expect(cmp.isMissing(param('a', 'TEXT', { required: true }))).toBe(true);
    cmp.values.set({ a: 'x' });
    expect(cmp.isMissing(param('a', 'TEXT', { required: true }))).toBe(false);
    expect(cmp.isMissing(param('b', 'BOOLEAN', { required: true }))).toBe(false);
    expect(cmp.isMissing(param('c', 'TEXT'))).toBe(false);
  });

  it('buildPayload coerces each value to its declared type', () => {
    cmp.params.set([
      param('text', 'TEXT'),
      param('num', 'NUMBER'),
      param('flag', 'BOOLEAN'),
      param('obj', 'OBJECT'),
      param('tags', 'TEXT', { isList: true }),
    ]);
    cmp.values.set({ text: 'hi', num: '42', flag: 'true', obj: '{"a":1}', tags: 'a\n b \n\nc' });
    const payload = acc.buildPayload();
    expect(payload).toEqual({ text: 'hi', num: 42, flag: true, obj: { a: 1 }, tags: ['a', 'b', 'c'] });
  });

  it('buildPayload nulls empty number/object and keeps invalid JSON as raw text', () => {
    cmp.params.set([param('num', 'NUMBER'), param('obj', 'OBJECT'), param('bad', 'OBJECT')]);
    cmp.values.set({ num: '', obj: '', bad: '{nope' });
    expect(acc.buildPayload()).toEqual({ num: null, obj: null, bad: '{nope' });
  });

  it('onScrim closes unless a run is in progress', () => {
    const closed = vi.fn();
    cmp.close.subscribe(closed);
    cmp.running.set(true);
    cmp.onScrim();
    expect(closed).not.toHaveBeenCalled();
    cmp.running.set(false);
    cmp.onScrim();
    expect(closed).toHaveBeenCalled();
  });

  it('run blocks on missing required fields (no backend call)', () => {
    cmp.params.set([param('a', 'TEXT', { required: true })]);
    cmp.values.set({ a: '' });
    cmp.run();
    expect(cmp.attempted()).toBe(true);
    expect(automation.runManually).not.toHaveBeenCalled();
  });

  it('run posts the payload and signals completion on success', () => {
    const ran = vi.fn();
    cmp.ran.subscribe(ran);
    cmp.params.set([param('text', 'TEXT')]);
    cmp.values.set({ text: 'hi' });
    cmp.run();
    expect(automation.runManually).toHaveBeenCalledWith('a1', { text: 'hi' });
    expect(cmp.done()).toBe(true);
    expect(ran).toHaveBeenCalled();
  });

  it('run surfaces a backend error', () => {
    automation.runManually.mockReturnValue(throwError(() => new Error('boom')));
    cmp.run(); // no params → runs with undefined payload
    expect(cmp.running()).toBe(false);
    expect(cmp.runError()).toBeTruthy();
  });

  it('load resolves the MANUAL trigger parameter set into the form', () => {
    automation.get.mockReturnValue(of({
      nodes: [{ nodeType: 'TRIGGER', config: JSON.stringify({ triggerMode: 'MANUAL', parameterSetId: 'ps1' }) }],
    }));
    paramSets.get.mockReturnValue(of({ parameters: [param('a', 'TEXT'), param('b', 'BOOLEAN')] }));
    acc.load('a1');
    expect(paramSets.get).toHaveBeenCalledWith('ps1');
    expect(cmp.params().length).toBe(2);
    expect(cmp.values()).toEqual({ a: '', b: 'false' });
    expect(cmp.loading()).toBe(false);
  });

  it('load degrades to a no-parameter run when the trigger has no parameter set', () => {
    automation.get.mockReturnValue(of({ nodes: [{ nodeType: 'TRIGGER', config: '{}' }] }));
    acc.load('a1');
    expect(paramSets.get).not.toHaveBeenCalled();
    expect(cmp.params()).toEqual([]);
    expect(cmp.loading()).toBe(false);
  });
});
