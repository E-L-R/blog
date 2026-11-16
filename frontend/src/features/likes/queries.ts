/**
 * Which items the current user likes. Post and comment responses can't say it (the backend's
 * `post` and `comment` modules don't know about likes, docs/DECISIONS.md, D13), so a page asks
 * once for all the items it shows. Internal to `likes`, apart from {@link useLikedIds}.
 */
import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { Id } from '@/api/types';
import { useAuth } from '@/features/auth';
import { fetchLikedIds, type LikeKind } from './api';

export const likeQueryKeys = {
  all: ['likes'] as const,
  kind: (kind: LikeKind) => [...likeQueryKeys.all, kind] as const,
  lookup: (kind: LikeKind, ids: Id[]) => [...likeQueryKeys.kind(kind), [...ids].sort()] as const,
};

/**
 * The IDs among `ids` that the current user likes, from one lookup (or one per 100 IDs).
 * Anonymous users like nothing, and nothing is asked.
 */
export function useLikedIds(kind: LikeKind, ids: Id[]): ReadonlySet<Id> {
  const { state } = useAuth();
  const enabled = state.status === 'authenticated' && ids.length > 0;
  const query = useQuery({
    queryKey: likeQueryKeys.lookup(kind, ids),
    queryFn: ({ signal }) => fetchLikedIds(kind, ids, signal),
    enabled,
    select: (likedIds) => new Set(likedIds),
  });
  return (enabled ? query.data : undefined) ?? EMPTY;
}

const EMPTY: ReadonlySet<Id> = new Set();

/**
 * Marks `id` as liked or not in every cached lookup that includes it, so all places that show
 * it agree at once. Returns a function that undoes the change.
 */
export function useSetCachedLike() {
  const queryClient = useQueryClient();
  return (kind: LikeKind, id: Id, liked: boolean) => {
    const before = queryClient.getQueriesData<Id[]>({ queryKey: likeQueryKeys.kind(kind) });
    for (const [key, likedIds] of before) {
      const ids = key[2] as Id[];
      if (likedIds === undefined || !ids.includes(id)) continue;
      const without = likedIds.filter((likedId) => likedId !== id);
      queryClient.setQueryData(key, liked ? [...without, id] : without);
    }
    return () => before.forEach(([key, likedIds]) => queryClient.setQueryData(key, likedIds));
  };
}
