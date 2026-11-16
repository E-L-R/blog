/**
 * A heart with the number of likes, which likes or unlikes a post or comment. Part of the
 * `likes` feature's public API.
 *
 * The click shows at once ("optimistic"): the heart fills and the count goes up before the API
 * answers, and both go back if the request fails. Anonymous users are sent to log in instead.
 */
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import type { Id } from '@/api/types';
import { useAuth } from '@/features/auth';
import { errorMessage } from '@/lib/errors';
import { setLiked, type LikeKind } from './api';
import { useSetCachedLike } from './queries';
import styles from './LikeButton.module.css';

interface LikeButtonProps {
  kind: LikeKind;
  id: Id;
  /** The like count from the post or comment. */
  count: number;
  /** Whether the current user likes it, from `useLikedIds`. */
  liked: boolean;
  size?: 'medium' | 'small';
  disabled?: boolean;
}

/**
 * The count shown after a click, until the post or comment is loaded again: `fromCount` is the
 * count the click started from. Once the loaded count differs, it includes the click.
 */
interface Pending {
  fromCount: number;
  count: number;
}

export function LikeButton({ kind, id, count, liked, size = 'medium', disabled }: LikeButtonProps) {
  const { state } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const setCachedLike = useSetCachedLike();
  const [pending, setPending] = useState<Pending | null>(null);

  const mutation = useMutation({
    mutationFn: (like: boolean) => setLiked(kind, id, like),
    onMutate: (like) => {
      setPending({ fromCount: count, count: Math.max(0, count + (like ? 1 : -1)) });
      return { undo: setCachedLike(kind, id, like) };
    },
    onError: (_error, _like, context) => {
      context?.undo();
      setPending(null);
    },
  });

  const shownCount = pending !== null && pending.fromCount === count ? pending.count : count;
  const what = kind === 'post' ? 'post' : 'comment';

  function handleClick() {
    if (state.status !== 'authenticated') {
      const redirect = encodeURIComponent(location.pathname + location.search);
      navigate(`/login?redirect=${redirect}`);
      return;
    }
    mutation.mutate(!liked);
  }

  return (
    <span>
      <button
        type="button"
        className={[styles.button, size === 'small' && styles.small, liked && styles.liked]
          .filter(Boolean)
          .join(' ')}
        aria-pressed={liked}
        aria-label={`${liked ? 'Unlike' : 'Like'} this ${what} (${shownCount} ${shownCount === 1 ? 'like' : 'likes'})`}
        onClick={handleClick}
        disabled={disabled}
      >
        <span className={styles.heart} aria-hidden="true">
          {liked ? '♥' : '♡'}
        </span>
        {/* A small heart with no likes yet shows no "0"; the label still says it. */}
        {(size === 'medium' || shownCount > 0) && <span aria-hidden="true">{shownCount}</span>}
      </button>
      {mutation.isError && (
        <span className={styles.error} role="alert">
          {errorMessage(mutation.error)}
        </span>
      )}
    </span>
  );
}
