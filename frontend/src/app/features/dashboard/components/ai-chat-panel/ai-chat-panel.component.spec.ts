import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AiChatPanelComponent } from './ai-chat-panel.component';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { PlanService } from '../../../../core/services/plan.service';

/**
 * Focuses on onMessagesClick — the in-app deep-link interception for links the assistant renders.
 * The component is created without change detection (no template render), so only the click-routing
 * logic is exercised against mocked Router / AiChatService.
 */
describe('AiChatPanelComponent deep-link interception', () => {
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };
  let chat: { isOpen: ReturnType<typeof signal<boolean>> };

  function setup() {
    router = { navigateByUrl: vi.fn() };
    chat = {
      isOpen: signal(true),
      messages: signal([]),
      streamingToolCalls: signal([]),
      selectedModel: signal('gemini-2.5-flash'),
    } as any;

    TestBed.configureTestingModule({
      imports: [AiChatPanelComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: AiChatService, useValue: chat },
        { provide: PlanService, useValue: { getUsage: () => of({ plan: { costLimitCents: 100 } }) } },
      ],
    });
    const comp = TestBed.createComponent(AiChatPanelComponent).componentInstance;
    return comp as unknown as { onMessagesClick: (e: MouseEvent) => void };
  }

  function clickOn(el: Element): MouseEvent {
    return { target: el, preventDefault: vi.fn() } as unknown as MouseEvent;
  }

  function anchor(href: string | null): HTMLAnchorElement {
    const a = document.createElement('a');
    if (href !== null) a.setAttribute('href', href);
    return a;
  }

  it('routes a root-relative internal link in-app and closes the panel', () => {
    const comp = setup();
    const evt = clickOn(anchor('/dashboard/email-accounts'));

    comp.onMessagesClick(evt);

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard/email-accounts');
    expect(chat.isOpen()).toBe(false);
    expect(evt.preventDefault).toHaveBeenCalled();
  });

  it('routes a same-origin absolute link by its path', () => {
    const comp = setup();
    const evt = clickOn(anchor(window.location.origin + '/dashboard/emails?q=1'));

    comp.onMessagesClick(evt);

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard/emails?q=1');
  });

  it('leaves an external link to the browser (no in-app navigation)', () => {
    const comp = setup();
    const evt = clickOn(anchor('https://example.com/pricing'));

    comp.onMessagesClick(evt);

    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(chat.isOpen()).toBe(true);
    expect(evt.preventDefault).not.toHaveBeenCalled();
  });

  it('ignores clicks that are not on a link', () => {
    const comp = setup();

    comp.onMessagesClick(clickOn(document.createElement('div')));

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('ignores an anchor without an href', () => {
    const comp = setup();

    comp.onMessagesClick(clickOn(anchor(null)));

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
