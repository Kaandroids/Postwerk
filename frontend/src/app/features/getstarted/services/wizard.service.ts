import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { I18nService } from '../../../core/services/i18n.service';
import {
  WizardMessage,
  WizardToolCall,
  WizardStreamEvent,
  WizardPlan,
  WizardPhase,
  WizardSessionResponse,
  WizardClaimResponse,
  WizardFlowNode,
  WizardFlowEdge,
  WizStageStatus,
  WizCaption,
  WizPacketState,
  WizStamp,
  WizPktAnimState,
} from '../models/wizard.model';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class WizardService {
  private readonly http = inject(HttpClient);
  private readonly i18n = inject(I18nService);
  private readonly basePath = `${environment.apiUrl}/wizard`;

  // ─── Core state ─────────────────────────────────────────────
  readonly phase = signal<WizardPhase>('chatting');
  readonly messages = signal<WizardMessage[]>([]);
  readonly sessionId = signal<string | null>(null);
  readonly isLoading = signal(false);
  readonly automationPlan = signal<WizardPlan | null>(null);
  readonly toolCalls = signal<WizardToolCall[]>([]);
  /** Accumulates all tool call results across messages (never cleared except on full reset). */
  readonly toolCallHistory = signal<WizardToolCall[]>([]);
  readonly narrationKey = signal<string>('');

  // ─── Animation state (driven by canvas component) ──────────
  readonly stageStatus = signal<WizStageStatus>('building');
  readonly activeNodeId = signal<string | null>(null);
  readonly doneNodes = signal<Set<string>>(new Set());
  readonly badgeNodes = signal<Set<string>>(new Set());
  readonly liveEdges = signal<Set<string>>(new Set());
  readonly classifyHit = signal<string | null>(null);
  readonly extractCount = signal(0);
  readonly replyText = signal('');
  readonly caption = signal<WizCaption | null>(null);
  readonly packet = signal<WizPacketState>({ on: false, from: '', subj: '', avatar: '', initials: '' });
  readonly stamps = signal<WizStamp[]>([]);
  readonly pktState = signal<WizPktAnimState>('idle');
  readonly showFoot = signal(false);

  // kept for backward compatibility with edge drawing
  readonly revealedNodes = signal<Set<string>>(new Set());
  readonly drawnEdges = signal<Set<string>>(new Set());

  private abortController: AbortController | null = null;

  constructor() {
    const storedId = sessionStorage.getItem('wizard_session_id');
    if (storedId) {
      this.sessionId.set(storedId);
    }
  }

  async sendMessage(text: string): Promise<void> {
    const userMessage: WizardMessage = {
      role: 'user',
      content: text,
      timestamp: new Date().toISOString(),
    };
    this.messages.update(msgs => [...msgs, userMessage]);
    await this.streamChat(text, false);
  }

  /**
   * Streams a single chat turn. If the backend reports the (Redis-backed)
   * session as expired/missing — e.g. a stale {@code wizard_session_id} restored
   * from {@code sessionStorage} after the 30-min TTL — the dead id is dropped and
   * the turn is retried once with a fresh session, so the user never has to
   * restart manually or lose their message.
   */
  private async streamChat(text: string, isRetry: boolean): Promise<void> {
    this.isLoading.set(true);
    this.toolCalls.set([]);

    const request = {
      sessionId: this.sessionId() ?? undefined,
      message: text,
      lang: this.i18n.lang(),
    };

    this.abortController = new AbortController();
    let retrySession = false;

    try {
      const response = await fetch(`${this.basePath}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
      const allToolCalls: WizardToolCall[] = [];

      outer:
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

          let event: WizardStreamEvent;
          try {
            event = JSON.parse(jsonStr);
          } catch {
            continue;
          }

          switch (event.type) {
            case 'tool_start':
              if (event.conversationId) {
                this.sessionId.set(event.conversationId);
                sessionStorage.setItem('wizard_session_id', event.conversationId);
              }
              this.toolCalls.update(calls => [
                ...calls,
                { tool: event.tool!, args: event.args || {}, result: null, success: false },
              ]);
              this.updateNarration(event.tool!);
              break;

            case 'tool_result':
              this.toolCalls.update(calls => {
                const updated = [...calls];
                for (let i = updated.length - 1; i >= 0; i--) {
                  if (updated[i].tool === event.tool && updated[i].result === null) {
                    updated[i] = { ...updated[i], result: event.result, success: event.success! };
                    break;
                  }
                }
                return updated;
              });
              allToolCalls.push({
                tool: event.tool!,
                args: {},
                result: event.result,
                success: event.success!,
              });
              this.handleToolResult(event.tool!, event.result);
              this.toolCallHistory.update(h => [...h, {
                tool: event.tool!,
                args: this.toolCalls().find(c => c.tool === event.tool && c.success)?.args ?? {},
                result: event.result,
                success: event.success!,
              }]);
              break;

            case 'reply':
              if (event.conversationId) {
                this.sessionId.set(event.conversationId);
                sessionStorage.setItem('wizard_session_id', event.conversationId);
              }
              replyContent = event.content || '';
              break;

            case 'phase':
              if (event.phase) {
                this.phase.set(event.phase as WizardPhase);
              }
              break;

            case 'error':
              if (!isRetry && this.sessionId() && this.isSessionExpired(event.content)) {
                retrySession = true;
              } else {
                this.messages.update(msgs => [...msgs, {
                  role: 'assistant',
                  content: event.content || 'An error occurred.',
                  timestamp: new Date().toISOString(),
                  error: true,
                }]);
              }
              await reader.cancel().catch(() => { /* noop */ });
              break outer;

            case 'done':
              if (event.conversationId) {
                this.sessionId.set(event.conversationId);
                sessionStorage.setItem('wizard_session_id', event.conversationId);
              }
              if (event.phase) {
                this.phase.set(event.phase as WizardPhase);
              }
              break;
          }
        }
      }

      if (!retrySession && replyContent) {
        this.messages.update(msgs => [...msgs, {
          role: 'assistant',
          content: replyContent,
          timestamp: new Date().toISOString(),
          toolCalls: allToolCalls.length > 0 ? allToolCalls : undefined,
        }]);
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      this.messages.update(msgs => [...msgs, {
        role: 'assistant',
        content: 'Connection error. Please try again.',
        timestamp: new Date().toISOString(),
        error: true,
      }]);
    } finally {
      this.abortController = null;
      if (!retrySession) this.isLoading.set(false);
    }

    if (retrySession) {
      this.sessionId.set(null);
      sessionStorage.removeItem('wizard_session_id');
      await this.streamChat(text, true);
    }
  }

  /** Whether an SSE error message indicates a stale/expired wizard session. */
  private isSessionExpired(content?: string): boolean {
    const c = (content || '').toLowerCase();
    return c.includes('session expired') || c.includes('not found');
  }

  claimSession(sessionId: string): Observable<WizardClaimResponse> {
    return this.http.post<WizardClaimResponse>(`${this.basePath}/claim`, { sessionId });
  }

  restoreSession(id: string): Observable<WizardSessionResponse> {
    return this.http.get<WizardSessionResponse>(`${this.basePath}/session/${id}`);
  }

  reset(): void {
    this.phase.set('chatting');
    this.messages.set([]);
    this.sessionId.set(null);
    this.isLoading.set(false);
    this.automationPlan.set(null);
    this.toolCalls.set([]);
    this.toolCallHistory.set([]);
    this.narrationKey.set('');
    this.revealedNodes.set(new Set());
    this.drawnEdges.set(new Set());
    // Reset animation state
    this.stageStatus.set('building');
    this.activeNodeId.set(null);
    this.doneNodes.set(new Set());
    this.badgeNodes.set(new Set());
    this.liveEdges.set(new Set());
    this.classifyHit.set(null);
    this.extractCount.set(0);
    this.replyText.set('');
    this.caption.set(null);
    this.packet.set({ on: false, from: '', subj: '', avatar: '', initials: '' });
    this.stamps.set([]);
    this.pktState.set('idle');
    this.showFoot.set(false);
    sessionStorage.removeItem('wizard_session_id');
  }

  cancel(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    this.isLoading.set(false);
  }

  private updateNarration(tool: string): void {
    switch (tool) {
      case 'propose_automation_plan':
        this.narrationKey.set('wiz_narr_analyze');
        break;
      case 'create_category':
      case 'create_filter':
      case 'create_parameter_set':
      case 'create_template':
        this.narrationKey.set('wiz_narr_plan');
        break;
      case 'create_automation':
        this.narrationKey.set('wiz_narr_build');
        break;
      case 'update_automation_flow':
        this.narrationKey.set('wiz_narr_connect');
        break;
      case 'list_categories':
      case 'list_filters':
        this.narrationKey.set('wiz_narr_understand');
        break;
      case 'run_automation_tests':
        this.narrationKey.set('wiz_narr_test');
        break;
    }
  }

  private handleToolResult(tool: string, result: unknown): void {
    if (tool === 'update_automation_flow' && result && typeof result === 'object') {
      const data = result as Record<string, unknown>;
      const nodes = data['nodes'] as WizardFlowNode[] | undefined;
      const edges = data['edges'] as WizardFlowEdge[] | undefined;
      if (nodes && edges) {
        this.automationPlan.set({
          automationId: data['automationId'] as string,
          nodes,
          edges,
        });
      }
    }
  }
}
