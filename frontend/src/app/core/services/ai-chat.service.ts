import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { I18nService } from './i18n.service';
import { TokenService } from './token.service';
import { WorkspaceService } from './workspace.service';
import { PlanService } from './plan.service';
import { environment } from '../../../environments/environment';
import {
  AiChatRequest,
  AiConversation,
  AiConversationDetail,
  AiMessage,
  AiStreamEvent,
  AiToolCall,
  ConversationPhase,
} from '../../models/ai-chat.model';

/**
 * Manages AI assistant chat sessions with SSE streaming support.
 * Sends messages via fetch (streaming) and parses SSE events for live tool call updates.
 */
@Injectable({ providedIn: 'root' })
export class AiChatService {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly token = inject(TokenService);
  private readonly workspace = inject(WorkspaceService);
  private readonly planService = inject(PlanService);
  private readonly basePath = '/ai';

  private readonly FOLDER_MUTATING_TOOLS = ['create_folder', 'delete_folder'];

  /** Maps a resource-mutating AI tool to the resource "type" key pages listen for, so an open
   *  resource page reloads after the assistant creates/updates/deletes something (no manual refresh). */
  private readonly RESOURCE_MUTATING_TOOLS: Record<string, string> = {
    create_category: 'categories', update_category: 'categories', delete_category: 'categories',
    create_template: 'templates', update_template: 'templates', delete_template: 'templates',
    create_parameter_set: 'parameterSets', update_parameter_set: 'parameterSets', delete_parameter_set: 'parameterSets',
    create_automation: 'automations', update_automation: 'automations',
    update_automation_flow: 'automations', delete_automation: 'automations',
  };

  readonly isOpen = signal(false);
  readonly messages = signal<AiMessage[]>([]);
  readonly conversations = signal<AiConversation[]>([]);
  readonly activeConversationId = signal<string | null>(null);
  readonly isLoading = signal(false);
  readonly lastFailedMessage = signal<string | null>(null);
  readonly streamingToolCalls = signal<AiToolCall[]>([]);
  readonly conversationPhase = signal<ConversationPhase>('OPEN');
  /** Bumped whenever the assistant mutates resources; resource pages watch this to reload their list. */
  readonly resourceMutation = signal<{ types: string[]; seq: number }>({ types: [], seq: 0 });
  readonly selectedModel = signal<'gemini-2.5-flash' | 'gemini-2.5-pro'>(
    (localStorage.getItem('ai_chat_model') as 'gemini-2.5-flash' | 'gemini-2.5-pro') || 'gemini-2.5-flash'
  );

  private abortController: AbortController | null = null;
  private static readonly MAX_TOOL_CALLS = 200;

  setModel(model: 'gemini-2.5-flash' | 'gemini-2.5-pro'): void {
    this.selectedModel.set(model);
    localStorage.setItem('ai_chat_model', model);
  }

  toggleOpen(): void {
    this.isOpen.update(v => !v);
    if (this.isOpen() && this.conversations().length === 0) {
      this.loadConversations();
    }
  }

  sendMessage(text: string): void {
    const userMessage: AiMessage = {
      role: 'user',
      content: text,
      timestamp: new Date().toISOString(),
    };
    this.messages.update(msgs => [...msgs, userMessage]);
    this.isLoading.set(true);
    this.lastFailedMessage.set(null);
    this.streamingToolCalls.set([]);

    this.doSendStream(text);
  }

  retry(): void {
    const failed = this.lastFailedMessage();
    if (!failed) return;

    this.messages.update(msgs => msgs.filter(m => !m.error));
    this.isLoading.set(true);
    this.lastFailedMessage.set(null);
    this.streamingToolCalls.set([]);

    this.doSendStream(failed);
  }

  cancelChat(): void {
    // Abort the fetch request
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }

    // Notify backend
    const convId = this.activeConversationId();
    if (convId) {
      this.api.post<void>(`${this.basePath}/chat/${convId}/cancel`, {}).subscribe({
        error: () => {},
      });
    }

    this.isLoading.set(false);
    this.streamingToolCalls.set([]);
  }

  private async doSendStream(text: string): Promise<void> {
    const request: AiChatRequest = {
      message: text,
      conversationId: this.activeConversationId() ?? undefined,
      model: this.selectedModel(),
      language: this.i18n.lang(),
    };

    this.abortController = new AbortController();
    const accessToken = this.token.getAccessToken();

    try {
      const response = await fetch(`${environment.apiUrl}${this.basePath}/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`,
        },
        body: JSON.stringify(request),
        signal: this.abortController.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let replyContent = '';
      const allToolCalls: AiToolCall[] = [];

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.startsWith('data:')) continue;
          const jsonStr = line.slice(5).trim();
          if (!jsonStr) continue;

          let event: AiStreamEvent;
          try {
            event = JSON.parse(jsonStr);
          } catch {
            continue;
          }

          switch (event.type) {
            case 'tool_start':
              this.activeConversationId.set(event.conversationId);
              this.streamingToolCalls.update(calls => {
                const updated = [
                  ...calls,
                  { tool: event.tool!, args: event.args || {}, result: null, success: false },
                ];
                // Cap streaming tool calls to prevent unbounded growth
                return updated.length > AiChatService.MAX_TOOL_CALLS
                  ? updated.slice(-AiChatService.MAX_TOOL_CALLS)
                  : updated;
              });
              break;

            case 'tool_result':
              this.streamingToolCalls.update(calls => {
                const updated = [...calls];
                // Find the last pending call for this tool
                let found = false;
                for (let i = updated.length - 1; i >= 0; i--) {
                  if (updated[i].tool === event.tool && updated[i].result === null) {
                    updated[i] = { ...updated[i], result: event.result, success: event.success!, validationIssues: event.validationIssues };
                    found = true;
                    break;
                  }
                }
                if (!found) {
                  console.warn(`Received tool_result for ${event.tool} but no pending call found`);
                }
                return updated;
              });
              allToolCalls.push({
                tool: event.tool!,
                args: this.streamingToolCalls().find(tc => tc.tool === event.tool && tc.result !== null)?.args || {},
                result: event.result,
                success: event.success!,
                validationIssues: event.validationIssues,
              });
              // Cap accumulated tool calls to prevent unbounded memory growth
              if (allToolCalls.length > AiChatService.MAX_TOOL_CALLS) {
                allToolCalls.splice(0, allToolCalls.length - AiChatService.MAX_TOOL_CALLS);
              }
              break;

            case 'reply':
              this.activeConversationId.set(event.conversationId);
              replyContent = event.content || '';
              break;

            case 'error':
              this.isLoading.set(false);
              this.lastFailedMessage.set(text);
              this.streamingToolCalls.set([]);
              this.messages.update(msgs => [...msgs, {
                role: 'assistant',
                content: '',
                timestamp: new Date().toISOString(),
                error: true,
              }]);
              return;

            case 'phase':
              if (event.phase) {
                this.conversationPhase.set(event.phase);
                this.messages.update(msgs => [...msgs, {
                  role: 'system',
                  content: event.phase!,
                  timestamp: new Date().toISOString(),
                  phase: event.phase,
                }]);
              }
              break;

            case 'done': {
              const assistantMessage: AiMessage = {
                role: 'assistant',
                content: replyContent,
                timestamp: new Date().toISOString(),
                toolCalls: allToolCalls.length > 0 ? allToolCalls : undefined,
              };
              this.messages.update(msgs => [...msgs, assistantMessage]);
              this.isLoading.set(false);
              this.streamingToolCalls.set([]);
              if (event.phase) {
                this.conversationPhase.set(event.phase);
              }
              this.loadConversations();
              // The turn just spent AI cost — refresh the shared usage signal so the topbar AI-limit
              // widget updates live without a page reload.
              this.planService.loadUsage();

              if (allToolCalls.some(tc => this.FOLDER_MUTATING_TOOLS.includes(tc.tool))) {
                this.workspace.loadFolders();
              }
              const mutatedTypes = [...new Set(
                allToolCalls
                  .map(tc => this.RESOURCE_MUTATING_TOOLS[tc.tool])
                  .filter((t): t is string => !!t),
              )];
              if (mutatedTypes.length > 0) {
                this.resourceMutation.update(s => ({ types: mutatedTypes, seq: s.seq + 1 }));
              }
              return;
            }

            case 'cancelled':
              this.isLoading.set(false);
              this.streamingToolCalls.set([]);
              this.messages.update(msgs => [...msgs, {
                role: 'system' as const,
                content: this.i18n.t('chat_cancelled'),
                timestamp: new Date().toISOString(),
              }]);
              return;
          }
        }
      }

      // Stream ended without done event — finalize
      if (replyContent || allToolCalls.length > 0) {
        const assistantMessage: AiMessage = {
          role: 'assistant',
          content: replyContent,
          timestamp: new Date().toISOString(),
          toolCalls: allToolCalls.length > 0 ? allToolCalls : undefined,
        };
        this.messages.update(msgs => [...msgs, assistantMessage]);
      }
      this.isLoading.set(false);
      this.streamingToolCalls.set([]);
      this.loadConversations();
      this.planService.loadUsage();
    } catch (e: unknown) {
      if (e instanceof Error && e.name === 'AbortError') {
        // User cancelled — already handled
        return;
      }
      this.isLoading.set(false);
      this.lastFailedMessage.set(text);
      this.streamingToolCalls.set([]);
      this.messages.update(msgs => [...msgs, {
        role: 'assistant',
        content: '',
        timestamp: new Date().toISOString(),
        error: true,
      }]);
    } finally {
      this.abortController = null;
    }
  }

  loadConversations(): void {
    this.api.get<AiConversation[]>(`${this.basePath}/conversations`).subscribe({
      next: (conversations) => this.conversations.set(conversations),
      error: () => { /* silently ignore — conversations list is non-critical */ },
    });
  }

  loadConversation(id: string): void {
    this.api.get<AiConversationDetail>(`${this.basePath}/conversations/${id}`).subscribe({
      next: (detail) => {
        this.activeConversationId.set(detail.id);
        this.messages.set(detail.messages);
        this.conversationPhase.set(detail.phase ?? 'OPEN');
      },
      error: () => { /* silently ignore — user can retry by clicking conversation again */ },
    });
  }

  confirmPlan(): void {
    this.sendMessage(this.i18n.t('chat_confirm_plan'));
  }

  cancelPlan(): void {
    this.sendMessage(this.i18n.t('chat_cancel_plan'));
  }

  newConversation(): void {
    this.activeConversationId.set(null);
    this.messages.set([]);
    this.lastFailedMessage.set(null);
    this.streamingToolCalls.set([]);
    this.conversationPhase.set('OPEN');
  }

  deleteConversation(id: string): void {
    this.api.delete<void>(`${this.basePath}/conversations/${id}`).subscribe({
      next: () => {
        this.conversations.update(convs => convs.filter(c => c.id !== id));
        if (this.activeConversationId() === id) {
          this.newConversation();
        }
      },
      error: () => { /* silently ignore — conversation remains in list */ },
    });
  }
}
