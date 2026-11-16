/**
 * The `posts` feature's API calls (docs/API.md#posts).
 */
import { apiRequest } from '@/api/client';
import type { Id, Page, Post, PostInput, PostSummary } from '@/api/types';

export const PAGE_SIZE = 10;

export interface PostListParams {
  /** Counts from 0. */
  page: number;
  /** Only this author's posts. */
  authorId?: Id;
}

/** `GET /posts`: newest first. */
export function listPosts(
  params: PostListParams,
  signal?: AbortSignal,
): Promise<Page<PostSummary>> {
  return apiRequest<Page<PostSummary>>('/posts', {
    query: { page: params.page, size: PAGE_SIZE, authorId: params.authorId },
    signal,
  });
}

/** `GET /posts/{id}`. */
export function getPost(id: Id, signal?: AbortSignal): Promise<Post> {
  return apiRequest<Post>(`/posts/${encodeURIComponent(id)}`, { signal });
}

/** `POST /posts`. */
export function createPost(input: PostInput): Promise<Post> {
  return apiRequest<Post>('/posts', { method: 'POST', body: input });
}

/** `PUT /posts/{id}`. Only the author may edit. */
export function updatePost(id: Id, input: PostInput): Promise<Post> {
  return apiRequest<Post>(`/posts/${encodeURIComponent(id)}`, { method: 'PUT', body: input });
}

/** `DELETE /posts/{id}`. Only the author may delete. */
export function deletePost(id: Id): Promise<void> {
  return apiRequest<void>(`/posts/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
