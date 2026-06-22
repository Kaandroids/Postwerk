import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal, viewChild, ElementRef, AfterViewChecked, effect } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { I18nService } from '../../../../core/services/i18n.service';
import { PlanService } from '../../../../core/services/plan.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { AiMessage, AiToolCall, ConversationPhase } from '../../../../models/ai-chat.model';

interface ToolField {
  key: string;
  label: string;
  value: string;
  type: 'text' | 'color';
}

/** Exact tool-name → icon overrides, checked before substring rules. */
const TOOL_ICON_EXACT: Record<string, string> = {
  propose_automation_plan: 'clipboard',
  run_automation_tests: 'play',
  list_automation_tests: 'list',
};

/** Ordered [substring, icon] rules; first match wins. */
const TOOL_ICON_RULES: [string, string][] = [
  ['category', 'tag'], ['filter', 'filter'], ['template', 'mail'],
  ['parameter', 'code'], ['automation', 'workflow'], ['folder', 'folder'],
  ['email_account', 'mailbox'],
];

/** Exact tool-name → action i18n key overrides, checked before prefix rules. */
const TOOL_ACTION_EXACT: Record<string, string> = {
  propose_automation_plan: 'chat_tool_proposed',
  run_automation_tests: 'chat_tool_ran_tests',
  list_automation_tests: 'chat_tool_listed_tests',
};

/** Ordered [prefix, action i18n key] rules; first match wins. */
const TOOL_ACTION_RULES: [string, string][] = [
  ['create_', 'chat_tool_created'], ['delete_', 'chat_tool_deleted'],
  ['update_', 'chat_tool_updated'], ['list_', 'chat_tool_listed'],
];

/** Exact tool-name → resource-type i18n key overrides, checked before substring rules. */
const TOOL_RESOURCE_EXACT: Record<string, string> = {
  propose_automation_plan: 'chat_tool_plan',
  run_automation_tests: 'chat_tool_tests',
  list_automation_tests: 'chat_tool_tests',
};

/** Ordered [substring, resource-type i18n key] rules; first match wins. */
const TOOL_RESOURCE_RULES: [string, string][] = [
  ['automation_test', 'chat_tool_tests'], ['category', 'chat_tool_category'],
  ['filter', 'chat_tool_filter'], ['template', 'chat_tool_template'],
  ['parameter', 'chat_tool_parameter_set'], ['automation_flow', 'chat_tool_flow'],
  ['automation', 'chat_tool_automation'], ['folder', 'chat_tool_folder'],
  ['email_account', 'chat_tool_email_account'],
];

/** Slide-in AI assistant chat panel with SSE streaming, markdown rendering, and tool call approval. */
@Component({
  selector: 'app-ai-chat-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, RouterLink],
  templateUrl: './ai-chat-panel.component.html',
  styleUrl: './ai-chat-panel.component.scss',
})
export class AiChatPanelComponent implements AfterViewChecked {
  protected chat = inject(AiChatService);
  protected i18n = inject(I18nService);
  private sanitizer = inject(DomSanitizer);
  private planService = inject(PlanService);

  protected aiDisabled = signal(false);
  protected inputText = signal('');
  protected showHistory = signal(false);
  protected expandedTools = signal<Set<number>>(new Set());
  protected copiedIndex = signal<number | null>(null);
  protected isProModel = () => this.chat.selectedModel() === 'gemini-2.5-pro';

  private messagesContainer = viewChild<ElementRef>('messagesContainer');
  private chatInput = viewChild<ElementRef>('chatInput');
  private shouldScroll = false;
  private htmlCache = new Map<string, SafeHtml>();

  protected starterPrompts: { icon: string; key: string; descKey: string }[] = [
    { icon: 'filter', key: 'chat_starter_filter', descKey: 'chat_starter_filter_desc' },
    { icon: 'tag', key: 'chat_starter_category', descKey: 'chat_starter_category_desc' },
    { icon: 'mail', key: 'chat_starter_template', descKey: 'chat_starter_template_desc' },
    { icon: 'zap', key: 'chat_starter_automation', descKey: 'chat_starter_automation_desc' },
  ];

  private destroyRef = inject(DestroyRef);

  constructor() {
    marked.setOptions({ breaks: true, gfm: true });
    this.planService.getUsage().pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      // AI is gated on the cost cap (costLimitCents === 0 = disabled), consistent with the backend
      // quota check and the topbar widget. tokenLimit is legacy and 0 even on cost-enabled plans
      // (e.g. STARTER with a small €0.10 budget), so gating on it would wrongly lock AI.
      next: (usage) => this.aiDisabled.set(usage.plan.costLimitCents === 0),
      error: () => {},
    });
    // Auto-scroll when messages or streaming tools change
    effect(() => {
      this.chat.messages();
      this.chat.streamingToolCalls();
      this.scheduleScroll();
    });
  }

  private scheduleScroll(): void {
    const el = this.messagesContainer()?.nativeElement;
    if (!el) { this.shouldScroll = true; return; }
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 150;
    if (nearBottom) this.shouldScroll = true;
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  protected renderMarkdown(content: string): SafeHtml {
    if (!content) return '';
    const cached = this.htmlCache.get(content);
    if (cached) return cached;
    const raw = marked.parse(content) as string;
    const clean = DOMPurify.sanitize(raw);
    const safe = this.sanitizer.bypassSecurityTrustHtml(clean);
    this.htmlCache.set(content, safe);
    return safe;
  }

  protected useStarter(key: string): void {
    const text = this.i18n.t(key);
    this.shouldScroll = true;
    this.chat.sendMessage(text);
  }

  protected send(): void {
    const text = this.inputText().trim();
    if (!text || this.chat.isLoading()) return;
    this.inputText.set('');
    this.shouldScroll = true;
    this.resetInputHeight();
    this.chat.sendMessage(text);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  protected autoResize(): void {
    const el = this.chatInput()?.nativeElement;
    if (!el) return;
    el.style.height = 'auto';
    const maxH = 140;
    const newH = Math.min(el.scrollHeight, maxH);
    el.style.height = newH + 'px';
    el.style.overflowY = el.scrollHeight > maxH ? 'auto' : 'hidden';
  }

  private resetInputHeight(): void {
    const el = this.chatInput()?.nativeElement;
    if (el) el.style.height = '';
  }

  protected retry(): void {
    this.shouldScroll = true;
    this.chat.retry();
  }

  protected cancel(): void {
    this.chat.cancelChat();
  }

  protected confirmPlan(): void {
    this.shouldScroll = true;
    this.chat.confirmPlan();
  }

  protected cancelPlan(): void {
    this.shouldScroll = true;
    this.chat.cancelPlan();
  }

  protected copyMessage(content: string, index: number): void {
    navigator.clipboard.writeText(content).then(() => {
      this.copiedIndex.set(index);
      setTimeout(() => this.copiedIndex.set(null), 2000);
    });
  }

  protected setModel(model: 'gemini-2.5-flash' | 'gemini-2.5-pro'): void {
    this.chat.setModel(model);
  }

  protected close(): void {
    this.chat.isOpen.set(false);
  }

  protected newChat(): void {
    this.chat.newConversation();
    this.showHistory.set(false);
    this.htmlCache.clear();
  }

  protected toggleHistory(): void {
    this.showHistory.update(v => !v);
    if (this.showHistory()) {
      this.chat.loadConversations();
    }
  }

  protected selectConversation(id: string): void {
    this.chat.loadConversation(id);
    this.showHistory.set(false);
    this.shouldScroll = true;
    this.htmlCache.clear();
  }

  protected deleteConversation(event: Event, id: string): void {
    event.stopPropagation();
    this.chat.deleteConversation(id);
  }

  protected toggleToolDetails(index: number): void {
    this.expandedTools.update(set => {
      const next = new Set(set);
      if (next.has(index)) { next.delete(index); } else { next.add(index); }
      return next;
    });
  }

  protected toolIcon(tc: AiToolCall): string {
    const t = tc.tool;
    if (TOOL_ICON_EXACT[t]) return TOOL_ICON_EXACT[t];
    return TOOL_ICON_RULES.find(([sub]) => t.includes(sub))?.[1] ?? 'check';
  }

  protected toolAction(tc: AiToolCall): string {
    const t = tc.tool;
    if (TOOL_ACTION_EXACT[t]) return this.i18n.t(TOOL_ACTION_EXACT[t]);
    const rule = TOOL_ACTION_RULES.find(([prefix]) => t.startsWith(prefix));
    return rule ? this.i18n.t(rule[1]) : t.replace(/_/g, ' ');
  }

  protected toolResourceType(tc: AiToolCall): string {
    const t = tc.tool;
    if (TOOL_RESOURCE_EXACT[t]) return this.i18n.t(TOOL_RESOURCE_EXACT[t]);
    const rule = TOOL_RESOURCE_RULES.find(([sub]) => t.includes(sub));
    return rule ? this.i18n.t(rule[1]) : '';
  }

  protected toolResourceName(tc: AiToolCall): string | null {
    if (!tc.success || !tc.result) return null;
    const r = tc.result as any;
    return r?.name || r?.data?.name || tc.args?.['name'] as string || null;
  }

  protected isListTool(tc: AiToolCall): boolean {
    return tc.tool.startsWith('list_') && Array.isArray(tc.result);
  }

  protected getListItems(tc: AiToolCall): string[] {
    const r = tc.result;
    if (!Array.isArray(r)) return [];
    return r.slice(0, 10).map((item: any) => item.name || item.email || '—');
  }

  protected getListCount(tc: AiToolCall): number {
    return Array.isArray(tc.result) ? tc.result.length : 0;
  }

  protected getToolFields(tc: AiToolCall): ToolField[] {
    const r = tc.result as any;
    if (!r || !tc.success) return [];
    const tool = tc.tool;

    if (tool.includes('category')) {
      return this.buildFields([
        ['name', 'chat_field_name', r.name, 'text'],
        ['color', 'chat_field_color', r.color, 'color'],
        ['description', 'chat_field_description', r.description, 'text'],
      ]);
    }
    if (tool.includes('filter')) {
      const condCount = r.groups?.reduce((s: number, g: any) => s + (g.conditions?.length || 0), 0) || 0;
      return this.buildFields([
        ['name', 'chat_field_name', r.name, 'text'],
        ['color', 'chat_field_color', r.color, 'color'],
        ['conditions', 'chat_field_conditions', condCount ? `${condCount}` : null, 'text'],
        ['description', 'chat_field_description', r.description, 'text'],
      ]);
    }
    if (tool.includes('template')) {
      return this.buildFields([
        ['name', 'chat_field_name', r.name, 'text'],
        ['subject', 'chat_field_subject', r.subject, 'text'],
      ]);
    }
    if (tool.includes('parameter')) {
      const paramCount = r.parameters?.length || 0;
      return this.buildFields([
        ['name', 'chat_field_name', r.name, 'text'],
        ['params', 'chat_field_parameters', paramCount ? `${paramCount}` : null, 'text'],
      ]);
    }
    if (tool === 'run_automation_tests') {
      const total = r.totalTests ?? r.results?.length ?? 0;
      const passed = r.passedTests ?? r.results?.filter((t: any) => t.passed)?.length ?? 0;
      const failed = total - passed;
      return this.buildFields([
        ['total', 'chat_field_total_tests', `${total}`, 'text'],
        ['passed', 'chat_field_passed', `${passed}`, 'text'],
        ['failed', 'chat_field_failed', failed > 0 ? `${failed}` : null, 'text'],
      ]);
    }
    if (tool === 'propose_automation_plan') {
      return this.buildFields([
        ['planSummary', 'chat_field_plan', r.planSummary, 'text'],
      ]);
    }
    if (tool.includes('automation')) {
      return this.buildFields([
        ['name', 'chat_field_name', r.name, 'text'],
        ['color', 'chat_field_color', r.color, 'color'],
        ['description', 'chat_field_description', r.description, 'text'],
        ['nodes', 'chat_field_nodes', r.nodeCount ? `${r.nodeCount}` : null, 'text'],
      ]);
    }
    return [];
  }

  private buildFields(entries: [string, string, string | null, 'text' | 'color'][]): ToolField[] {
    return entries
      .filter(([,, value]) => value != null && value !== '')
      .map(([key, labelKey, value, type]) => ({
        key, label: this.i18n.t(labelKey), value: value!, type
      }));
  }

  protected phaseIcon(phase?: ConversationPhase): string {
    switch (phase) {
      case 'PLANNING': return 'clipboard';
      case 'BUILDING': return 'workflow';
      case 'OPEN': return 'check';
      default: return 'info';
    }
  }

  protected phaseLabel(phase?: ConversationPhase): string {
    switch (phase) {
      case 'PLANNING': return this.i18n.t('chat_phase_msg_planning');
      case 'BUILDING': return this.i18n.t('chat_phase_msg_building');
      case 'OPEN': return this.i18n.t('chat_phase_msg_open');
      default: return '';
    }
  }

  protected trackMessage(_: number, msg: AiMessage): string {
    return msg.timestamp + (msg.error ? '_err' : '') + (msg.role === 'system' ? '_sys' : '');
  }

  private scrollToBottom(): void {
    const el = this.messagesContainer()?.nativeElement;
    if (el) { el.scrollTop = el.scrollHeight; }
  }
}
