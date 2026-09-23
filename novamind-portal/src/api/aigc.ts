import request from '@/utils/request'
import type { ApiResponse } from '@/types'
import type {
  AssistantSessionBootstrap,
  ChatHistoryMessage,
  ChatSessionHistoryGroups,
  PromptTemplateMap,
  SessionExample,
} from '@/types/aigc'

type MaybeWrapped<T> = ApiResponse<T> | T

function unwrapPayload<T>(payload: MaybeWrapped<T>): T {
  if (
    payload &&
    typeof payload === 'object' &&
    'code' in payload &&
    'data' in payload
  ) {
    return (payload as ApiResponse<T>).data
  }
  return payload as T
}

export interface ChatRequest {
  question: string
  sessionId?: string
  mediaType?: string
  mediaUrl?: string
}

export function chat(data: ChatRequest) {
  return request.post<ReadableStream, { responseType: 'stream' }>('/ais/chat', data, {
    responseType: 'stream',
  })
}

export function stopChat(sessionId: string) {
  return request
    .post<MaybeWrapped<void>, MaybeWrapped<void>>('/ais/chat/stop', null, {
      params: { sessionId },
    })
    .then(unwrapPayload)
}

export function chatText(question: string) {
  return request
    .post<MaybeWrapped<string>, MaybeWrapped<string>>('/ais/chat/text', question, {
      headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
    })
    .then(unwrapPayload)
}

export function getChatTemplates() {
  return request
    .get<MaybeWrapped<PromptTemplateMap>, MaybeWrapped<PromptTemplateMap>>('/ais/chat/templates')
    .then(unwrapPayload)
}

export function audioToText(formData: FormData) {
  return request
    .post<MaybeWrapped<string>, MaybeWrapped<string>>('/ais/audio/stt', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then(unwrapPayload)
}

export function textToAudioStream(data: { text: string }) {
  return request.post('/ais/audio/tts-stream', data, {
    responseType: 'blob',
  })
}

export function createSession(num = 4) {
  return request
    .post<MaybeWrapped<AssistantSessionBootstrap>, MaybeWrapped<AssistantSessionBootstrap>>('/ais/session', null, {
      params: { n: num },
    })
    .then(unwrapPayload)
}

export function getHotSessions(num = 6) {
  return request
    .get<MaybeWrapped<SessionExample[]>, MaybeWrapped<SessionExample[]>>('/ais/session/hot', {
      params: { n: num },
    })
    .then(unwrapPayload)
}

export function getSessionHistory(sessionId: string) {
  return request
    .get<MaybeWrapped<ChatHistoryMessage[]>, MaybeWrapped<ChatHistoryMessage[]>>(`/ais/session/${sessionId}`)
    .then(unwrapPayload)
}

export function getAllSessions() {
  return request
    .get<MaybeWrapped<ChatSessionHistoryGroups>, MaybeWrapped<ChatSessionHistoryGroups>>('/ais/session/history')
    .then(unwrapPayload)
}

export function deleteSessionHistory(sessionId: string) {
  return request
    .delete<MaybeWrapped<void>, MaybeWrapped<void>>('/ais/session/history', {
      params: { sessionId },
    })
    .then(unwrapPayload)
}

export function updateSessionTitle(sessionId: string, title: string) {
  return request
    .put<MaybeWrapped<void>, MaybeWrapped<void>>('/ais/session/history', null, {
      params: { sessionId, title },
    })
    .then(unwrapPayload)
}
