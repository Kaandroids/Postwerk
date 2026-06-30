import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { TestModePanelComponent } from './test-mode-panel.component';
import { AutomationService } from '../../../../core/services/automation.service';
import { provideStubI18n } from '../../../../../testing';

/**
 * Logic-only spec (no template render): the component is created and its methods / computed signals
 * are exercised directly, with AutomationService stubbed. ngOnInit (the live poller) is never run.
 */
function makeService() {
  return {
    getTestModeStats: vi.fn(() => of({ correct: 0, incorrect: 0, accuracyPercent: 0 })),
    getTestModeResults: vi.fn(() => of({ content: [], totalElements: 0 })),
    submitTestModeFeedback: vi.fn(() => of({})),
    clearTestModeResults: vi.fn(() => of(undefined)),
    deleteTestModeResult: vi.fn(() => of(undefined)),
  };
}

describe('TestModePanelComponent', () => {
  let svc: ReturnType<typeof makeService>;
  let cmp: TestModePanelComponent;

  beforeEach(() => {
    svc = makeService();
    TestBed.configureTestingModule({
      imports: [TestModePanelComponent],
      providers: [provideStubI18n(), { provide: AutomationService, useValue: svc }],
    });
    const fixture = TestBed.createComponent(TestModePanelComponent);
    fixture.componentRef.setInput('automationId', 'a1');
    cmp = fixture.componentInstance;
  });

  it('grade() maps feedback to an accent keyword', () => {
    expect(cmp.grade({ feedback: 'CORRECT' } as never)).toBe('correct');
    expect(cmp.grade({ feedback: 'INCORRECT' } as never)).toBe('wrong');
    expect(cmp.grade({ feedback: null } as never)).toBe('pending');
  });

  it('initials() derives two upper-case letters from the sender local-part', () => {
    expect(cmp.initials(null)).toBe('··');
    expect(cmp.initials('john.doe@x.com')).toBe('JO');
    expect(cmp.initials('a@x.com')).toBe('A');
  });

  it('accuracyLabel is a dash until graded, then a percentage', () => {
    expect(cmp.accuracyLabel()).toBe('–');
    cmp.stats.set({ correct: 3, incorrect: 1, accuracyPercent: 75 } as never);
    expect(cmp.accuracyLabel()).toBe('75%');
  });

  it('simulatedCount sums the simulated actions across the feed', () => {
    cmp.results.set([{ simulatedActions: [1, 2] }, { simulatedActions: [3] }] as never);
    expect(cmp.simulatedCount()).toBe(3);
  });

  it('hasNextPage reflects page*20 vs the total', () => {
    cmp.totalElements.set(50);
    cmp.page.set(0);
    expect(cmp.hasNextPage()).toBe(true);
    cmp.page.set(2);
    expect(cmp.hasNextPage()).toBe(false);
  });

  it('setFilter resets the page to 0 and reloads', () => {
    cmp.page.set(3);
    cmp.setFilter('CORRECT');
    expect(cmp.filter()).toBe('CORRECT');
    expect(cmp.page()).toBe(0);
    expect(svc.getTestModeResults).toHaveBeenCalledWith('a1', 'CORRECT', 0);
  });

  it('loadResults stores content + total and clears loading', () => {
    svc.getTestModeResults.mockReturnValue(of({ content: [{ id: 'r1', simulatedActions: [] }], totalElements: 1 }) as never);
    cmp.loadResults();
    expect(cmp.results().length).toBe(1);
    expect(cmp.totalElements()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('submitFeedback replaces the result in place and refreshes stats', () => {
    cmp.results.set([{ id: 'r1', feedback: null }] as never);
    svc.submitTestModeFeedback.mockReturnValue(of({ id: 'r1', feedback: 'CORRECT' }));
    cmp.submitFeedback({ id: 'r1' } as never, 'CORRECT');
    expect(cmp.results()[0].feedback).toBe('CORRECT');
    expect(svc.getTestModeStats).toHaveBeenCalled();
  });

  it('deleteResult removes the row and decrements the total', () => {
    cmp.results.set([{ id: 'r1' }, { id: 'r2' }] as never);
    cmp.totalElements.set(2);
    cmp.deleteResult({ id: 'r1' } as never);
    expect(cmp.results().map(r => r.id)).toEqual(['r2']);
    expect(cmp.totalElements()).toBe(1);
  });

  it('clearResults empties the feed', () => {
    cmp.results.set([{ id: 'r1' }] as never);
    cmp.totalElements.set(1);
    cmp.clearResults();
    expect(cmp.results()).toEqual([]);
    expect(cmp.totalElements()).toBe(0);
  });

  it('nextPage / prevPage move the page (clamped at 0) and reload', () => {
    cmp.nextPage();
    expect(cmp.page()).toBe(1);
    cmp.prevPage();
    expect(cmp.page()).toBe(0);
    cmp.prevPage();
    expect(cmp.page()).toBe(0);
  });
});
