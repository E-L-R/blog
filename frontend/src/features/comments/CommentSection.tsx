/**
 * A post's comments: the thread, a box for a new comment, and "Ask AI" for the post. Part of
 * the `comments` feature's public API. Uses `likes` for the hearts and `ai` for the AI's
 * replies, which arrive as comments by the bot.
 */
import { useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router';
import type { Id } from '@/api/types';
import { Loading, QueryError } from '@/components/States';
import { AskAiButton } from '@/features/ai';
import { useAuth } from '@/features/auth';
import { useLikedIds } from '@/features/likes';
import { CommentForm } from './CommentForm';
import { CommentItem } from './CommentItem';
import { useCreateComment, useRefreshThread, useThread } from './queries';
import { buildThread, countVisible } from './thread';
import styles from './Comments.module.css';

export function CommentSection({ postId }: { postId: Id }) {
  const { state } = useAuth();
  const location = useLocation();
  const query = useThread(postId);
  const create = useCreateComment(postId);
  const refresh = useRefreshThread(postId);
  const [highlightId, setHighlightId] = useState<Id | null>(null);

  const comments = query.data;
  const thread = useMemo(() => (comments ? buildThread(comments) : []), [comments]);
  const ids = useMemo(() => (comments ?? []).map((comment) => comment.id), [comments]);
  const likedIds = useLikedIds('comment', ids);

  function highlight(id: Id) {
    setHighlightId(id);
    void refresh();
  }

  const loginLink = `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`;

  return (
    <section className={styles.section} aria-labelledby="comments-title">
      <header className={styles.header}>
        <h2 id="comments-title" className={styles.title}>
          Comments
          {comments && <span className={styles.count}>{countVisible(comments)}</span>}
        </h2>
        <AskAiButton target={{ kind: 'post', id: postId }} onReplied={highlight} />
      </header>

      {state.status === 'authenticated' ? (
        <CommentForm
          label="Write a comment"
          submitLabel="Comment"
          placeholder="Join the conversation…"
          busy={create.isPending}
          error={create.error}
          onSubmit={async (content) => {
            const comment = await create.mutateAsync({ content, parentId: null });
            setHighlightId(comment.id);
          }}
        />
      ) : (
        <p className={styles.prompt}>
          <Link to={loginLink}>Log in</Link> to join the conversation.
        </p>
      )}

      {query.isPending ? (
        <Loading label="Loading comments…" />
      ) : query.isError ? (
        <QueryError error={query.error} onRetry={() => void query.refetch()} />
      ) : thread.length === 0 ? (
        <p className={styles.empty}>No comments yet. Start the conversation, or ask the AI.</p>
      ) : (
        <div className={styles.list}>
          {thread.map((node) => (
            <CommentItem
              key={node.comment.id}
              node={node}
              depth={0}
              likedIds={likedIds}
              highlightId={highlightId}
              onHighlight={highlight}
            />
          ))}
        </div>
      )}
    </section>
  );
}
