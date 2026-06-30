import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ComposePanelComponent } from './compose-panel.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { EmailService } from '../../../../core/services/email.service';

interface Access {
  modeTitle(): string;
  canSend(): boolean;
  onSend(): void;
  onSaveDraft(): void;
  onDiscard(): void;
  removeAttachment(a: { id: string }): void;
  formatSize(b: number): string;
  onBodyChange(h: string): void;
}

describe('ComposePanelComponent', () => {
  let workspace: { activeAccount: ReturnType<typeof vi.fn> };
  let email: Record<string, ReturnType<typeof vi.fn>>;
  let cmp: ComposePanelComponent;
  let acc: Access;

  beforeEach(() => {
    workspace = { activeAccount: vi.fn(() => ({ id: 'acc' })) };
    email = {
      send: vi.fn(() => of({})),
      saveDraft: vi.fn(() => of({ id: 'd1' })),
      updateDraft: vi.fn(() => of({ id: 'd1' })),
      deleteDraft: vi.fn(() => of(undefined)),
      listAttachments: vi.fn(() => of([])),
      deleteAttachment: vi.fn(() => of(undefined)),
      uploadAttachment: vi.fn(() => of({ id: 'at1' })),
    };
    TestBed.configureTestingModule({
      imports: [ComposePanelComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: WorkspaceService, useValue: workspace },
        { provide: EmailService, useValue: email },
      ],
    });
    cmp = TestBed.createComponent(ComposePanelComponent).componentInstance;
    acc = cmp as unknown as Access;
  });

  it('openCompose resets the form and opens in compose mode', () => {
    cmp.to.set('x'); cmp.openCompose();
    expect(cmp.to()).toBe('');
    expect(cmp.mode()).toBe('compose');
    expect(cmp.isOpen()).toBe(true);
  });

  it('openReply prefills recipient + Re: subject (no double prefix) + quoted body', () => {
    cmp.openReply({ fromAddress: 'a@b.c', subject: 'Hello', bodyText: 'hi' } as never);
    expect(cmp.to()).toBe('a@b.c');
    expect(cmp.subject()).toBe('Re: Hello');
    expect(cmp.body()).toContain('hi');
    cmp.openReply({ fromAddress: 'a@b.c', subject: 'Re: Hello' } as never);
    expect(cmp.subject()).toBe('Re: Hello');
  });

  it('openForward sets the Fwd: subject', () => {
    cmp.openForward({ subject: 'Hello' } as never);
    expect(cmp.subject()).toBe('Fwd: Hello');
    expect(cmp.mode()).toBe('forward');
  });

  it('openDraft loads the draft fields and its attachments', () => {
    email['listAttachments'].mockReturnValue(of([{ id: 'a1' }]));
    cmp.openDraft({ id: 'd9', toAddresses: 'a@b.c', ccAddresses: 'c@d.e', subject: 'S', bodyHtml: '<p>x</p>' } as never);
    expect(cmp.draftId()).toBe('d9');
    expect(cmp.to()).toBe('a@b.c');
    expect(cmp.showCcBcc()).toBe(true);
    expect(cmp.attachments().length).toBe(1);
  });

  it('modeTitle maps each mode to a label key', () => {
    cmp.mode.set('reply');
    expect(acc.modeTitle()).toBe('compose_reply');
  });

  it('canSend requires recipient + subject and not sending', () => {
    expect(acc.canSend()).toBe(false);
    cmp.to.set('a@b.c'); cmp.subject.set('Hi');
    expect(acc.canSend()).toBe(true);
    cmp.sending.set(true);
    expect(acc.canSend()).toBe(false);
  });

  it('onSend posts, emits sent, and closes', () => {
    const sent = vi.fn();
    cmp.sent.subscribe(sent);
    cmp.to.set('a@b.c'); cmp.subject.set('Hi');
    acc.onSend();
    expect(email['send']).toHaveBeenCalled();
    expect(sent).toHaveBeenCalled();
    expect(cmp.isOpen()).toBe(false);
  });

  it('onSend is a no-op without an active account', () => {
    workspace.activeAccount.mockReturnValue(null);
    acc.onSend();
    expect(email['send']).not.toHaveBeenCalled();
  });

  it('onSaveDraft creates then updates the draft', () => {
    acc.onSaveDraft();
    expect(email['saveDraft']).toHaveBeenCalled();
    expect(cmp.draftId()).toBe('d1');
    acc.onSaveDraft();
    expect(email['updateDraft']).toHaveBeenCalled();
  });

  it('onDiscard deletes an existing draft and closes', () => {
    cmp.draftId.set('d1');
    acc.onDiscard();
    expect(email['deleteDraft']).toHaveBeenCalledWith('acc', 'd1');
    expect(cmp.isOpen()).toBe(false);
  });

  it('removeAttachment deletes and prunes the list', () => {
    cmp.draftId.set('d1');
    cmp.attachments.set([{ id: 'a1' }, { id: 'a2' }] as never);
    acc.removeAttachment({ id: 'a1' });
    expect(cmp.attachments().map(a => a.id)).toEqual(['a2']);
  });

  it('formatSize renders B / KB / MB', () => {
    expect(acc.formatSize(500)).toBe('500 B');
    expect(acc.formatSize(2048)).toBe('2 KB');
    expect(acc.formatSize(5 * 1024 * 1024)).toBe('5.0 MB');
  });

  it('onBodyChange stores the html', () => {
    acc.onBodyChange('<p>hi</p>');
    expect(cmp.body()).toBe('<p>hi</p>');
  });
});
