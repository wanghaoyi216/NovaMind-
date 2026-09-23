/**
 * Chat and ReAct related types
 * Validates: Requirements 6.3, 6.4, 6.5, 6.6, 6.7, 6.8
 */

/**
 * Event types emitted by the ReAct orchestrator
 */
export enum EventType {
  START = 'START',
  THOUGHT = 'THOUGHT',
  ACTION = 'ACTION',
  OBSERVATION = 'OBSERVATION',
  ANSWER = 'ANSWER',
  ERROR = 'ERROR',
  COMPLETE = 'COMPLETE'
}

/**
 * Action types that the reasoning engine can decide
 */
export enum ActionType {
  USE_TOOL = 'USE_TOOL',
  FINAL_ANSWER = 'FINAL_ANSWER',
  ASK_CLARIFICATION = 'ASK_CLARIFICATION',
  DELEGATE_TO_AGENT = 'DELEGATE_TO_AGENT'
}

/**
 * Tool categories for filtering and organization
 */
export enum ToolCategory {
  COURSE_QUERY = 'COURSE_QUERY',
  USER_QUERY = 'USER_QUERY',
  TRADE_OPERATION = 'TRADE_OPERATION',
  EXAM_OPERATION = 'EXAM_OPERATION',
  VECTOR_SEARCH = 'VECTOR_SEARCH'
}

/**
 * Message interface representing a chat message
 * Requirement 6.3: Message interface with id, type, content, thoughts, timestamp
 */
export interface Message {
  id: string
  type: 'user' | 'assistant'
  content: string
  thoughts?: ThoughtStep[]
  timestamp: number
}

/**
 * ThoughtStep interface representing a single reasoning step
 * Requirement 6.4: ThoughtStep interface with stepNumber, thought, action, observation
 */
export interface ThoughtStep {
  stepNumber: number
  thought: string
  action?: ActionEvent
  observation?: string
}

/**
 * ActionEvent interface representing a tool invocation
 * Requirement 6.5: ActionEvent interface with stepNumber, toolName, parameters, result, timestamp
 */
export interface ActionEvent {
  stepNumber: number
  toolName: string
  parameters: Record<string, any>
  result?: any
  timestamp: number
}

/**
 * AnswerEvent interface representing the final answer
 * Requirement 6.6: AnswerEvent interface with content, sources, confidence, timestamp
 */
export interface AnswerEvent {
  content: string
  sources?: Source[]
  confidence?: number
  timestamp: number
}

/**
 * Source reference for answer provenance
 */
export interface Source {
  title: string
  url?: string
  type: 'tool' | 'knowledge' | 'context'
}

/**
 * ChatEventVO interface matching backend model
 * Requirement 6.7, 6.8: ChatEventVO interface with sessionId, eventType, content, metadata, timestamp
 */
export interface ChatEventVO {
  sessionId: string
  eventType: EventType
  content: string
  metadata?: Record<string, any>
  timestamp: number
}

/**
 * Chat request sent to the backend
 */
export interface ChatRequest {
  sessionId: string
  question: string
}

/**
 * Tool definition for display and invocation
 */
export interface Tool {
  name: string
  description: string
  parameters: ToolParameter[]
  category: ToolCategory
  requiresAuth: boolean
}

/**
 * Tool parameter definition
 */
export interface ToolParameter {
  name: string
  type: string
  description: string
  required: boolean
  enum?: string[]
}

/**
 * Tool call request
 */
export interface ToolCall {
  toolName: string
  parameters: Record<string, any>
}

/**
 * Tool execution result
 */
export interface ActionResult {
  success: boolean
  data?: any
  error?: string
  executionTime?: number
}

/**
 * ReAct context for managing conversation state
 */
export interface ReActContext {
  sessionId: string
  userId: string
  currentQuestion: string
  conversationHistory: ChatMessage[]
  steps: ReActStep[]
  iterationCount: number
  agentType?: string
  metadata?: Record<string, any>
}

/**
 * Chat message stored in conversation history
 */
export interface ChatMessage {
  sessionId: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: number
  metadata?: Record<string, any>
}

/**
 * ReAct step representing one iteration of the loop
 */
export interface ReActStep {
  stepNumber: number
  thought: string
  action?: ActionDecision
  observation?: string
  timestamp: number
  status: StepStatus
}

/**
 * Step status enum
 */
export enum StepStatus {
  THINKING = 'THINKING',
  ACTING = 'ACTING',
  OBSERVING = 'OBSERVING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED'
}

/**
 * Action decision made by the reasoning engine
 */
export interface ActionDecision {
  type: ActionType
  toolName?: string
  parameters?: Record<string, any>
  reasoning?: string
}

/**
 * Conversation summary for memory management
 */
export interface ConversationSummary {
  sessionId: string
  summary: string
  keyPoints: string[]
  timestamp: number
}

/**
 * Session information
 */
export interface ChatSession {
  sessionId: string
  userId: string
  title: string
  createdAt: number
  updatedAt: number
  messageCount: number
}
