/** Request payload for sending a message to the AI assistant. */
export interface AiChatRequest {
  message: string;
  conversationId?: string;
  model?: string;
  /** UI language code ('de' | 'en') so the assistant pins its reply language instead of guessing
   *  from a possibly very short message (e.g. a one-word "Build automation" button press). */
  language?: string;
}

/** Response from the AI assistant containing a reply and any tool invocations. */
export interface AiChatResponse {
  conversationId: string;
  reply: string;
  toolCalls: AiToolCall[];
  phase: ConversationPhase;
}

/** Conversation phase for automation planning flow. */
export type ConversationPhase = 'OPEN' | 'PLANNING' | 'BUILDING';

/** A validation issue surfaced by the automation validator after a flow change. */
export interface AiValidationIssue {
  code: string;
  severity: 'error' | 'warning';
  nodeId: string | null;
  message: string;
}

/** Represents a single tool invocation performed by the AI during a conversation turn. */
export interface AiToolCall {
  tool: string;
  args: Record<string, unknown>;
  result: unknown;
  success: boolean;
  validationIssues?: AiValidationIssue[];
}

/** Summary representation of an AI conversation used in listing views. */
export interface AiConversation {
  id: string;
  title: string;
  updatedAt: string;
}

/** Full AI conversation including its complete message history. */
export interface AiConversationDetail {
  id: string;
  title: string;
  messages: AiMessage[];
  phase: ConversationPhase;
}

/** A single message within an AI conversation, from either the user or assistant. */
export interface AiMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
  toolCalls?: AiToolCall[];
  error?: boolean;
  phase?: ConversationPhase;
}

/** Server-Sent Event from the streaming AI chat endpoint. */
export interface AiStreamEvent {
  type: 'tool_start' | 'tool_result' | 'reply' | 'error' | 'done' | 'cancelled' | 'phase';
  tool?: string;
  args?: Record<string, unknown>;
  result?: unknown;
  success?: boolean;
  content?: string;
  conversationId: string;
  phase?: ConversationPhase;
  validationIssues?: AiValidationIssue[];
}
