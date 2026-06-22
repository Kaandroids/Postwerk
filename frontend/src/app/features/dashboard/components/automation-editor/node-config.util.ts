/**
 * Pure helpers backing the read-only node getters that are shared between the
 * automation-editor canvas previews and the node config side-panel.
 *
 * <p>Extracted verbatim from {@code AutomationEditorComponent} so the canvas
 * (parent) and the {@code NodeConfigPanelComponent} (child) can each keep
 * identically-named thin wrapper methods that delegate here — neither template
 * needs to change. Behaviour is unchanged from the inline implementations.</p>
 */
import { I18nService } from '../../../../core/services/i18n.service';
import { Category } from '../../../../models/category.model';
import {
  NodeType,
  NODE_PALETTE,
  TriggerMode,
  TriggerNodeConfig,
  CategorizeNodeConfig,
  DelayNodeConfig,
  LabelNodeConfig,
  RemoveLabelNodeConfig,
  FilterNodeConfig,
  FilterCheck,
  ExtractNodeConfig,
  ExtractionEntry,
  WebhookNodeConfig,
  CRON_PRESETS,
  getNodeDescKey,
} from '../../../../models/automation.model';
import { parseCronTime, parseCronDay, parseCronDayOfMonth } from './cron.util';

/** Minimal structural view of an editor node needed by the shared getters. */
export interface NodeConfigView {
  id: string;
  nodeType: NodeType;
  label: string;
  config: string;
}

export interface WeekDayOption { value: number; labelKey: string; }

/** Parses a node's config JSON, returning {@link fallback} when the node is absent or the JSON is invalid. */
export function parseNodeConfig<T>(node: NodeConfigView | undefined, fallback: T): T {
  if (!node) return fallback;
  try { return JSON.parse(node.config || '{}') as T; } catch { return fallback; }
}

export function nodeTypeLabel(i18n: I18nService, type: NodeType): string {
  const item = NODE_PALETTE.find(p => p.type === type);
  return item ? i18n.t(item.labelKey) : type;
}

export function nodeDescription(i18n: I18nService, type: NodeType): string {
  return i18n.t(getNodeDescKey(type));
}

export function triggerMode(node: NodeConfigView): TriggerMode {
  return parseNodeConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig).triggerMode || 'EMAIL';
}

export function scheduleDisplayText(i18n: I18nService, weekDays: WeekDayOption[], node: NodeConfigView): string {
  const config = parseNodeConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig);
  {
    const preset = config.preset || 'hourly';
    if (preset === 'custom' && config.cronExpression) return `CRON: ${config.cronExpression}`;
    if (preset === 'daily') {
      const time = parseCronTime(config.cronExpression);
      return `${i18n.t('auto_schedule_daily_short')} ${time}`;
    }
    if (preset === 'weekly') {
      const time = parseCronTime(config.cronExpression);
      const day = parseCronDay(config.cronExpression);
      const dayLabel = weekDays.find(d => d.value === day);
      const dayName = dayLabel ? i18n.t(dayLabel.labelKey) : '';
      return `${dayName} ${time}`;
    }
    if (preset === 'monthly') {
      const time = parseCronTime(config.cronExpression);
      const dom = parseCronDayOfMonth(config.cronExpression);
      return `${dom}. ${i18n.t('auto_schedule_monthly')} ${time}`;
    }
    const p = CRON_PRESETS.find(pr => pr.key === preset);
    if (p) return i18n.t(p.labelKey);
    return (config.intervalMinutes || 60) + ' min';
  }
}

export function categoryEntries(categories: Category[], node: NodeConfigView): { categoryId: string; label: string; color: string }[] {
  const config = parseNodeConfig<CategorizeNodeConfig>(node, {} as CategorizeNodeConfig);
  return (config.categoryIds || []).map(id => {
    const cat = categories.find(c => c.id === id);
    return { categoryId: id, label: cat?.name || id, color: cat?.color || 'var(--fg-muted)' };
  });
}

export function delayMinutes(node: NodeConfigView): number {
  return parseNodeConfig<DelayNodeConfig>(node, {} as DelayNodeConfig).delayMinutes ?? 30;
}

export function labelCategoryId(node: NodeConfigView): string {
  return parseNodeConfig<LabelNodeConfig>(node, {}).categoryId || '';
}

export function labelCategoryName(categories: Category[], node: NodeConfigView): string {
  const catId = labelCategoryId(node);
  if (!catId) return '';
  const cat = categories.find(c => c.id === catId);
  return cat?.name || '';
}

export function labelCategoryColor(categories: Category[], node: NodeConfigView): string {
  const catId = labelCategoryId(node);
  if (!catId) return 'var(--fg-muted)';
  const cat = categories.find(c => c.id === catId);
  return cat?.color || 'var(--fg-muted)';
}

export function removeLabelCategoryId(node: NodeConfigView): string {
  return parseNodeConfig<RemoveLabelNodeConfig>(node, { categoryId: '' }).categoryId || '';
}

export function removeLabelCategoryName(categories: Category[], node: NodeConfigView): string {
  const catId = removeLabelCategoryId(node);
  if (!catId) return '';
  const cat = categories.find(c => c.id === catId);
  return cat?.name || '';
}

export function webhookMethod(node: NodeConfigView): string {
  return parseNodeConfig<Record<string, unknown>>(node, {})['method'] as string || 'POST';
}

export function filterChecks(node: NodeConfigView): FilterCheck[] {
  return parseNodeConfig<FilterNodeConfig>(node, {} as FilterNodeConfig).checks || [];
}

export function extractions(node: NodeConfigView): ExtractionEntry[] {
  return parseNodeConfig<ExtractNodeConfig>(node, {} as ExtractNodeConfig).extractions || [];
}
