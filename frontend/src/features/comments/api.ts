/**
 * The `comments` feature's API calls (docs/API.md#comments).
 */
import { apiRequest } from '@/api/client';
import type { Comment, CommentInput, Id } from '@/api/types';

/** `GET /posts/{id}/comments`: the whole thread, as a flat list, oldest first. */
export function listComments(postId: Id, signal?: AbortSignal): Promise<Comment[]> {
  return apiRequest<Comment[]>(`/posts/${encodeURIComponent(postId)}/comments`, { signal });
}

/** `POST /posts/{id}/comments`. */
export function createComment(postId: Id, input: CommentInput): Promise<Comment> {
  return apiRequest<Comment>(`/posts/${encodeURIComponent(postId)}/comments`, {
    method: 'POST',
    body: input,
  });
}

/** `PUT /comments/{id}`. Only the author may edit, and not once the comment is deleted. */
export function updateComment(id: Id, content: string): Promise<Comment> {
  return apiRequest<Comment>(`/comments/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: { content },
  });
}

/** `DELETE /comments/{id}`: a soft delete. The comment keeps its place and its replies. */
export function deleteComment(id: Id): Promise<void> {
  return apiRequest<void>(`/comments/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
