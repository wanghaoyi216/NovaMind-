export interface SessionExample {
  title: string
  describe: string
}

export interface AssistantSessionBootstrap {
  sessionId: string
  title: string
  describe: string
  examples: SessionExample[]
}

export interface ChatSessionSummary {
  sessionId: string
  title: string
  updateTime: string
}

export type ChatSessionHistoryGroups = Record<string, ChatSessionSummary[]>

export interface ChatHistoryMessage {
  type: 'USER' | 'ASSISTANT' | number | string
  content: string
  params?: Record<string, unknown>
}

export interface PromptTemplateMap {
  associationalWord: string
  helpedWrite: string
  continuedWrite: string
  polish: string
  streamline: string
}
