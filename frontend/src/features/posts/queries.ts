/**
 * TanStack Query hooks for posts: they fetch, cache and refresh the data, and keep the cache
 * right after a change. Internal to `posts`, apart from {@link postQueryKeys}.
 *
 * Every query is identified by a key. Keys start with `'posts'`, so one call can mark every
 * cached list and post as out of date.
 */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Id, Post, PostInput } from '@/api/types';
import { createPost, deletePost, getPost, listPosts, updatePost, type PostListParams } from './api';

/** How often to ask again while a TL;DR is being written. */
const TLDR_POLL_INTERVAL = 3000;

export const postQueryKeys = {
  all: ['posts'] as const,
  lists: () => [...postQueryKeys.all, 'list'] as const,
  list: (params: PostListParams) => [...postQueryKeys.lists(), params] as const,
  detail: (id: Id) => [...postQueryKeys.all, 'detail', id] as const,
};

export function usePostList(params: PostListParams) {
  return useQuery({
    queryKey: postQueryKeys.list(params),
    queryFn: ({ signal }) => listPosts(params, signal),
    // Keeps the current page on screen while the next one loads.
    placeholderData: keepPreviousData,
  });
}

export function usePost(id: Id) {
  return useQuery({
    queryKey: postQueryKeys.detail(id),
    queryFn: ({ signal }) => getPost(id, signal),
    // The backend writes the TL;DR in the background (docs/ARCHITECTURE.md#async-ai-processing).
    // Asking again every few seconds shows it as soon as it is ready.
    refetchInterval: (query) =>
      query.state.data?.tldrStatus === 'PENDING' ? TLDR_POLL_INTERVAL : false,
  });
}

export function useCreatePost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: PostInput) => createPost(input),
    onSuccess: (post) => {
      queryClient.setQueryData(postQueryKeys.detail(post.id), post);
      return queryClient.invalidateQueries({ queryKey: postQueryKeys.lists() });
    },
  });
}

export function useUpdatePost(id: Id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: PostInput) => updatePost(id, input),
    onSuccess: (post: Post) => {
      queryClient.setQueryData(postQueryKeys.detail(id), post);
      return queryClient.invalidateQueries({ queryKey: postQueryKeys.lists() });
    },
  });
}

export function useDeletePost(id: Id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => deletePost(id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: postQueryKeys.detail(id) });
      return queryClient.invalidateQueries({ queryKey: postQueryKeys.lists() });
    },
  });
}
