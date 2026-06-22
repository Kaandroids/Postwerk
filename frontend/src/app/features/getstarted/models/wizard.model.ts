export interface WizardMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
  toolCalls?: WizardToolCall[];
  phase?: string;
  error?: boolean;
}

export interface WizardToolCall {
  tool: string;
  args: Record<string, unknown>;
  result: unknown;
  success: boolean;
}

export interface WizardStreamEvent {
  type: 'tool_start' | 'tool_result' | 'reply' | 'error' | 'phase' | 'done' | 'cancelled';
  tool?: string;
  args?: Record<string, unknown>;
  result?: unknown;
  success?: boolean;
  content?: string;
  conversationId?: string;
  phase?: string;
}

export interface WizardPlan {
  planSummary?: string;
  automationId?: string;
  nodes?: WizardFlowNode[];
  edges?: WizardFlowEdge[];
}

export interface WizardFlowNode {
  id: string;
  nodeType: string;
  label: string;
  positionX: number;
  positionY: number;
  config: string;
}

export interface WizardFlowEdge {
  id: string;
  sourceNodeId: string;
  sourceHandle: string;
  targetNodeId: string;
  targetHandle: string;
}

export interface WizardSessionResponse {
  sessionId: string;
  phase: string;
  messages: WizardMessage[];
  automationPlan: WizardPlan | null;
}

export interface WizardClaimResponse {
  automationId: string;
}

export type WizardPhase = 'chatting' | 'building' | 'ready';

// ─── Canvas stage types ───────────────────────────────────────

export type WizNodeKind = 'trigger' | 'filter' | 'classify' | 'extract' | 'action';

export interface WizNodeBody {
  kind: 'chip' | 'outs' | 'cats' | 'fields' | 'reply';
  chip?: { icon: string; text: string };
  outs?: { tag: string; type: 'hit' | 'miss' }[];
  cats?: { id: string; name: string; color: string; conf: string }[];
  fields?: { key: string; value: string }[];
  reply?: string;
  fieldLabel?: string;
}

export interface WizPacketState {
  on: boolean;
  from: string;
  subj: string;
  avatar: string;
  initials: string;
}

export interface WizStamp {
  id: string;
  key: string;
  color: string;
  angle: number;
}

export interface WizCaption {
  text: string;
  color: string;
}

export interface WizNodeAnchor {
  cx: number;
  cy: number;
  w: number;
  h: number;
  top: number;
  bottom: number;
  left: number;
  right: number;
}

export type WizStageStatus = 'building' | 'running' | 'ready';

export type WizPktAnimState = 'idle' | 'arrive' | 'pulse' | 'fly';

// ─── Demo email run types ────────────────────────────────────

export interface WizDemoStop {
  nodeId: string;
  caption: string;
  captionColor: string;
  classify?: boolean;
  classifyHitId?: string;
  extract?: boolean;
  extractFieldCount?: number;
  reply?: boolean;
  replyFull?: string;
  stamp?: { key: string; color: string };
  badge?: boolean;
  sideEdge?: string;
  sideNodeId?: string;
  sideCaption?: string;
  sideCaptionColor?: string;
  fly?: string;
}

export interface WizDemoRun {
  id: string;
  from: string;
  subj: string;
  avatar: string;
  initials: string;
  edgePath: string[];
  stops: WizDemoStop[];
}
