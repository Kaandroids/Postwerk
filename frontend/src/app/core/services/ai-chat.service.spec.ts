import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AiChatService } from './ai-chat.service';
import { WorkspaceService } from './workspace.service';
import { PlanService } from './plan.service';
import { createMockApi, MockApi, provideMockApi, provideStubI18n } from '../../../testing';

/**
 * Covers the non-streaming surface of the AI chat service (the SSE `fetch` path is exercised by e2e):
 * model selection, open/close, conversation CRUD, and cancel.
 */
describe('AiChatService', () => {
  let api: MockApi;
  let service: AiChatService;

  beforeEach(() => {
    localStorage.clear();
    api = createMockApi();
    TestBed.configureTestingModule({
      providers: [
        provideMockApi(api),
        provideStubI18n(),
        { provide: WorkspaceService, useValue: { loadFolders: vi.fn() } },
        { provide: PlanService, useValue: { loadUsage: vi.fn() } },
      ],
    });
    service = TestBed.inject(AiChatService);
  });

  it('setModel() updates the signal and persists the choice', () => {
    service.setModel('gemini-2.5-pro');
    expect(service.selectedModel()).toBe('gemini-2.5-pro');
    expect(localStorage.getItem('ai_chat_model')).toBe('gemini-2.5-pro');
  });

  it('toggleOpen() opens and loads conversations once when the list is empty', () => {
    api.get.mockReturnValue(of([{ id: 'c1' }]));
    service.toggleOpen();
    expect(service.isOpen()).toBe(true);
    expect(api.get).toHaveBeenCalledWith('/ai/conversations');
    expect(service.conversations().length).toBe(1);
  });

  it('toggleOpen() does not reload when conversations are already present', () => {
    service.conversations.set([{ id: 'c1' } as never]);
    api.get.mockClear();
    service.toggleOpen();
    expect(service.isOpen()).toBe(true);
    expect(api.get).not.toHaveBeenCalled();
  });

  it('loadConversations() stores the fetched list', () => {
    api.get.mockReturnValue(of([{ id: 'c1' }, { id: 'c2' }]));
    service.loadConversations();
    expect(service.conversations().length).toBe(2);
  });

  it('loadConversation() loads the detail into the active conversation', () => {
    api.get.mockReturnValue(of({ id: 'c1', messages: [{ role: 'user', content: 'hi' }], phase: 'PLANNING' }));
    service.loadConversation('c1');
    expect(api.get).toHaveBeenCalledWith('/ai/conversations/c1');
    expect(service.activeConversationId()).toBe('c1');
    expect(service.messages().length).toBe(1);
    expect(service.conversationPhase()).toBe('PLANNING');
  });

  it('cancelChat() notifies the backend when a conversation is active', () => {
    service.activeConversationId.set('c1');
    service.isLoading.set(true);
    service.cancelChat();
    expect(api.post).toHaveBeenCalledWith('/ai/chat/c1/cancel', {});
    expect(service.isLoading()).toBe(false);
  });

  it('cancelChat() skips the backend call when there is no active conversation', () => {
    service.cancelChat();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('newConversation() resets the conversation state', () => {
    service.activeConversationId.set('c1');
    service.messages.set([{ role: 'user', content: 'x' } as never]);
    service.conversationPhase.set('BUILDING');
    service.newConversation();
    expect(service.activeConversationId()).toBeNull();
    expect(service.messages()).toEqual([]);
    expect(service.conversationPhase()).toBe('OPEN');
  });

  it('deleteConversation() removes the entry and resets when it was active', () => {
    api.delete.mockReturnValue(of(undefined));
    service.conversations.set([{ id: 'c1' }, { id: 'c2' }] as never);
    service.activeConversationId.set('c1');
    service.deleteConversation('c1');
    expect(api.delete).toHaveBeenCalledWith('/ai/conversations/c1');
    expect(service.conversations().map(c => c.id)).toEqual(['c2']);
    expect(service.activeConversationId()).toBeNull();
  });

  it('deleteConversation() keeps the active conversation when a different one is removed', () => {
    api.delete.mockReturnValue(of(undefined));
    service.conversations.set([{ id: 'c1' }, { id: 'c2' }] as never);
    service.activeConversationId.set('c1');
    service.deleteConversation('c2');
    expect(service.conversations().map(c => c.id)).toEqual(['c1']);
    expect(service.activeConversationId()).toBe('c1');
  });
});
