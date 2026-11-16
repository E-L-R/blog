/**
 * The `likes` feature's API calls (docs/API.md#likes).
 */
import { apiRequest } from '@/api/client';
import type { Id, LikedIds } from '@/api/types';

/** What can be liked. */
export type LikeKind = 'post' | 'comment';

/** The API takes at most this many IDs in one lookup. */
export const MAX_LOOKUP_IDS = 100;

const PATHS: Record<LikeKind, string> = { post: 'posts', comment: 'comments' };

/** `PUT` or `DELETE /posts/{id}/like` (or `/comments/{id}/like`). Both are idempotent. */
export function setLiked(kind: LikeKind, id: Id, liked: boolean): Promise<void> {
  return apiRequest<void>(`/${PATHS[kind]}/${encodeURIComponent(id)}/like`, {
    method: liked ? 'PUT' : 'DELETE',
  });
}

/**
 * `GET /me/likes/posts?ids=…` (or `/comments`): which of these the current user likes. More
 * than {@link MAX_LOOKUP_IDS} IDs are looked up in several requests.
 */
export async function fetchLikedIds(
  kind: LikeKind,
  ids: Id[],
  signal?: AbortSignal,
): Promise<Id[]> {
  const chunks: Id[][] = [];
  for (let i = 0; i < ids.length; i += MAX_LOOKUP_IDS)
    chunks.push(ids.slice(i, i + MAX_LOOKUP_IDS));
  const answers = await Promise.all(
    chunks.map((chunk) =>
      apiRequest<LikedIds>(`/me/likes/${PATHS[kind]}`, { query: { ids: chunk.join(',') }, signal }),
    ),
  );
  return answers.flatMap((answer) => answer.likedIds);
}
