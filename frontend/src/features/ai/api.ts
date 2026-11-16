/**
 * The `ai` feature's API calls (docs/API.md#ai).
 */
import { apiRequest } from '@/api/client';
import type { AiRequest, Id } from '@/api/types';

/** What the AI can reply to. */
export type AiTarget = { kind: 'post'; id: Id } | { kind: 'comment'; id: Id };

/** `POST /posts/{id}/ai-reply` or `/comments/{id}/ai-reply`: queues the request (`202`). */
export function requestReply(target: AiTarget): Promise<AiRequest> {
  const path = target.kind === 'post' ? 'posts' : 'comments';
  return apiRequest<AiRequest>(`/${path}/${encodeURIComponent(target.id)}/ai-reply`, {
    method: 'POST',
  });
}

/** `GET /ai-requests/{id}`: only the user who asked can see it. */
export function getAiRequest(id: Id, signal?: AbortSignal): Promise<AiRequest> {
  return apiRequest<AiRequest>(`/ai-requests/${encodeURIComponent(id)}`, { signal });
}
