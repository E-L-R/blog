/**
 * TanStack Query hooks for a post's comments. Internal to `comments`.
 *
 * The thread is loaded as one list, and every change reloads it: a thread is small, and the
 * reload also brings in what others wrote in the meantime.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Id } from '@/api/types';
import { createComment, deleteComment, listComments, updateComment } from './api';

export const commentQueryKeys = {
  all: ['comments'] as const,
  thread: (postId: Id) => [...commentQueryKeys.all, 'thread', postId] as const,
};

export function useThread(postId: Id) {
  return useQuery({
    queryKey: commentQueryKeys.thread(postId),
    queryFn: ({ signal }) => listComments(postId, signal),
  });
}

export function useRefreshThread(postId: Id) {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: commentQueryKeys.thread(postId) });
}

export function useCreateComment(postId: Id) {
  const refresh = useRefreshThread(postId);
  return useMutation({
    mutationFn: ({ content, parentId }: { content: string; parentId: Id | null }) =>
      createComment(postId, { content, parentId }),
    onSuccess: refresh,
  });
}

export function useUpdateComment(postId: Id, commentId: Id) {
  const refresh = useRefreshThread(postId);
  return useMutation({
    mutationFn: (content: string) => updateComment(commentId, content),
    onSuccess: refresh,
  });
}

export function useDeleteComment(postId: Id, commentId: Id) {
  const refresh = useRefreshThread(postId);
  return useMutation({
    mutationFn: () => deleteComment(commentId),
    onSuccess: refresh,
  });
}
