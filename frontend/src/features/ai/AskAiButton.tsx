/**
 * "Ask AI": asks the AI bot to reply to a post or a comment, and shows its progress until the
 * reply is there. Part of the `ai` feature's public API.
 *
 * The bot's answer is an ordinary comment. This button doesn't show it: it calls `onReplied`
 * with the new comment's ID, and the thread shows it.
 */
import { useMutation } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router';
import type { Id } from '@/api/types';
import { useAuth } from '@/features/auth';
import { errorMessage } from '@/lib/errors';
import type { AiTarget } from './api';
import { askAndWait } from './waitForReply';
import styles from './AskAiButton.module.css';

interface AskAiButtonProps {
  target: AiTarget;
  /** Called with the bot's comment once it is written. */
  onReplied: (commentId: Id) => void;
  size?: 'medium' | 'small';
}

export function AskAiButton({ target, onReplied, size = 'medium' }: AskAiButtonProps) {
  const { state } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const mutation = useMutation({
    mutationFn: () => askAndWait(target),
    onSuccess: (request) => {
      if (request.status === 'COMPLETED' && request.resultCommentId) {
        onReplied(request.resultCommentId);
      }
    },
  });

  function handleClick() {
    if (state.status !== 'authenticated') {
      const redirect = encodeURIComponent(location.pathname + location.search);
      navigate(`/login?redirect=${redirect}`);
      return;
    }
    mutation.mutate();
  }

  const request = mutation.data;
  const working = mutation.isPending;
  let status: { text: string; failed?: boolean } | null = null;
  if (working) status = { text: 'The AI is writing a reply…' };
  else if (mutation.isError) status = { text: errorMessage(mutation.error), failed: true };
  else if (request?.status === 'FAILED' || request?.status === 'CANCELLED') {
    status = { text: "The AI couldn't reply this time. Try again.", failed: true };
  } else if (request && request.status !== 'COMPLETED') {
    status = { text: 'The AI is taking a while. Its reply will appear here once it is written.' };
  }

  return (
    <span className={styles.wrapper}>
      <button
        type="button"
        className={[styles.button, size === 'small' && styles.small, working && styles.working]
          .filter(Boolean)
          .join(' ')}
        onClick={handleClick}
        disabled={working}
        aria-label={
          target.kind === 'post'
            ? 'Ask AI to reply to this post'
            : 'Ask AI to reply to this comment'
        }
      >
        <span className={styles.spark} aria-hidden="true">
          ✦
        </span>
        Ask AI
      </button>
      {status && (
        <span
          className={[styles.status, status.failed && styles.failed].filter(Boolean).join(' ')}
          role="status"
        >
          {status.text}
        </span>
      )}
    </span>
  );
}
