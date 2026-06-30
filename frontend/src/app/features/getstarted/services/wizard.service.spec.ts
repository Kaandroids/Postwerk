import { HttpTestingController } from '@angular/common/http/testing';
import { WizardService } from './wizard.service';
import { provideStubI18n, setupHttpService } from '../../../../testing';

/** Typed view of the private helpers exercised here (the streaming path is covered via e2e). */
interface WizardPriv {
  updateNarration(tool: string): void;
  handleToolResult(tool: string, result: unknown): void;
  isSessionExpired(content?: string): boolean;
}

describe('WizardService', () => {
  beforeEach(() => sessionStorage.clear());

  function build(): { service: WizardService; httpMock: HttpTestingController } {
    return setupHttpService(WizardService, [provideStubI18n()]);
  }

  it('restores a stored session id on construction', () => {
    sessionStorage.setItem('wizard_session_id', 's1');
    const { service, httpMock } = build();
    expect(service.sessionId()).toBe('s1');
    httpMock.verify();
  });

  it('claimSession() POSTs the session id', () => {
    const { service, httpMock } = build();
    service.claimSession('s1').subscribe();
    const req = httpMock.expectOne(r => r.url.endsWith('/wizard/claim'));
    expect(req.request.body).toEqual({ sessionId: 's1' });
    req.flush({});
    httpMock.verify();
  });

  it('restoreSession() GETs the session by id', () => {
    const { service, httpMock } = build();
    service.restoreSession('s1').subscribe();
    httpMock.expectOne(r => r.url.endsWith('/wizard/session/s1')).flush({});
    httpMock.verify();
  });

  it('reset() clears state and the stored session id', () => {
    sessionStorage.setItem('wizard_session_id', 's1');
    const { service, httpMock } = build();
    service.messages.set([{ role: 'user', content: 'x', timestamp: 't' }] as never);
    service.isLoading.set(true);
    service.reset();
    expect(service.messages()).toEqual([]);
    expect(service.sessionId()).toBeNull();
    expect(service.phase()).toBe('chatting');
    expect(service.isLoading()).toBe(false);
    expect(sessionStorage.getItem('wizard_session_id')).toBeNull();
    httpMock.verify();
  });

  it('cancel() stops the loading state', () => {
    const { service, httpMock } = build();
    service.isLoading.set(true);
    service.cancel();
    expect(service.isLoading()).toBe(false);
    httpMock.verify();
  });

  it('updateNarration maps tools to narration keys', () => {
    const { service, httpMock } = build();
    const priv = service as unknown as WizardPriv;
    priv.updateNarration('create_automation');
    expect(service.narrationKey()).toBe('wiz_narr_build');
    priv.updateNarration('update_automation_flow');
    expect(service.narrationKey()).toBe('wiz_narr_connect');
    priv.updateNarration('run_automation_tests');
    expect(service.narrationKey()).toBe('wiz_narr_test');
    httpMock.verify();
  });

  it('handleToolResult builds the automation plan from a flow update', () => {
    const { service, httpMock } = build();
    (service as unknown as WizardPriv).handleToolResult('update_automation_flow', {
      automationId: 'a1', nodes: [{}], edges: [{}],
    });
    expect(service.automationPlan()?.automationId).toBe('a1');
    httpMock.verify();
  });

  it('isSessionExpired detects stale-session error text', () => {
    const { service, httpMock } = build();
    const priv = service as unknown as WizardPriv;
    expect(priv.isSessionExpired('Session expired')).toBe(true);
    expect(priv.isSessionExpired('record not found')).toBe(true);
    expect(priv.isSessionExpired('hello')).toBe(false);
    expect(priv.isSessionExpired()).toBe(false);
    httpMock.verify();
  });
});
