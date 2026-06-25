import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { SimulateEmailDialogComponent } from './simulate-email-dialog.component';
import { AutomationService } from '../../../core/services/automation.service';
import { Automation, TestModeResult } from '../../../models/automation.model';

function auto(partial: Partial<Automation>): Automation {
  return {
    id: 'a1', name: 'A', kind: 'AUTOMATION', type: 'EMAIL', status: 'TESTING',
    color: '#fff', nodeCount: 3,
    ...partial,
  } as unknown as Automation;
}

describe('SimulateEmailDialogComponent', () => {
  let svc: { list: ReturnType<typeof vi.fn>; simulateEmail: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    svc = { list: vi.fn(), simulateEmail: vi.fn() };
    TestBed.configureTestingModule({
      imports: [SimulateEmailDialogComponent],
      providers: [provideRouter([]), { provide: AutomationService, useValue: svc }],
    });
  });

  function create(emailId = 'email-1') {
    const fixture = TestBed.createComponent(SimulateEmailDialogComponent);
    fixture.componentRef.setInput('emailId', emailId);
    return fixture.componentInstance;
  }

  it('keeps only email-triggered, non-integration automations', () => {
    svc.list.mockReturnValue(of([
      auto({ id: 'mail', kind: 'AUTOMATION' }),
      auto({ id: 'intg', kind: 'INTEGRATION' }),
    ]));
    const comp = create();

    comp.ngOnInit();

    expect(comp.loading()).toBe(false);
    expect(comp.automations().map((a) => a.id)).toEqual(['mail']);
  });

  it('surfaces a load error when listing fails', () => {
    svc.list.mockReturnValue(throwError(() => new Error('boom')));
    const comp = create();

    comp.ngOnInit();

    expect(comp.loading()).toBe(false);
    expect(comp.loadError()).not.toBe('');
  });

  it('runs a simulation and exposes the result on pick', () => {
    svc.list.mockReturnValue(of([auto({ id: 'mail' })]));
    const result = { id: 'r1', simulatedActions: [] } as unknown as TestModeResult;
    svc.simulateEmail.mockReturnValue(of(result));
    const comp = create('email-9');
    comp.ngOnInit();

    comp.pick(auto({ id: 'mail' }));

    expect(svc.simulateEmail).toHaveBeenCalledWith('mail', 'email-9');
    expect(comp.result()).toBe(result);
    expect(comp.running()).toBe(false);
    expect(comp.selected()?.id).toBe('mail');
  });

  it('surfaces a run error and clears the selection on pick failure', () => {
    svc.list.mockReturnValue(of([auto({ id: 'mail' })]));
    svc.simulateEmail.mockReturnValue(throwError(() => new Error('nope')));
    const comp = create();
    comp.ngOnInit();

    comp.pick(auto({ id: 'mail' }));

    expect(comp.runError()).not.toBe('');
    expect(comp.running()).toBe(false);
    expect(comp.selected()).toBeNull();
  });

  it('reset() clears the current selection and result', () => {
    svc.list.mockReturnValue(of([auto({ id: 'mail' })]));
    svc.simulateEmail.mockReturnValue(of({ id: 'r1', simulatedActions: [] } as unknown as TestModeResult));
    const comp = create();
    comp.ngOnInit();
    comp.pick(auto({ id: 'mail' }));

    comp.reset();

    expect(comp.selected()).toBeNull();
    expect(comp.result()).toBeNull();
  });
});
