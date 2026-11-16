/**
 * One comment with its replies, and what the user can do with it: like, reply, ask the AI,
 * and for their own comments, edit and delete. Internal to `comments`.
 *
 * A deleted comment keeps its place, so its replies still make sense, but shows "[deleted]"
 * and offers no actions (docs/DOMAIN.md#flows).
 */
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import type { Id } from '@/api/types';
import { Alert } from '@/components/Alert';
import { AuthorName } from '@/components/AuthorName';
import { Button } from '@/components/Button';
import { TimeAgo } from '@/components/TimeAgo';
import { AskAiButton } from '@/features/ai';
import { useAuth } from '@/features/auth';
import { LikeButton } from '@/features/likes';
import { errorMessage } from '@/lib/errors';
import { CommentForm } from './CommentForm';
import { useCreateComment, useDeleteComment, useUpdateComment } from './queries';
import type { ThreadNode } from './thread';
import styles from './Comments.module.css';

/** Deeper replies are not indented any further. */
const MAX_INDENT = 4;

interface CommentItemProps {
  node: ThreadNode;
  depth: number;
  likedIds: ReadonlySet<Id>;
  highlightId: Id | null;
  /** Marks a comment as new, so it is highlighted and scrolled to. */
  onHighlight: (id: Id) => void;
}

type Mode = 'view' | 'reply' | 'edit' | 'delete';

export function CommentItem({ node, depth, likedIds, highlightId, onHighlight }: CommentItemProps) {
  const { comment, replies } = node;
  const { state } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState<Mode>('view');
  const create = useCreateComment(comment.postId);
  const update = useUpdateComment(comment.postId, comment.id);
  const remove = useDeleteComment(comment.postId, comment.id);

  const isMine =
    state.status === 'authenticated' &&
    comment.author !== null &&
    comment.author.id === state.me.id;
  const edited = !comment.deleted && comment.updatedAt !== comment.createdAt;
  const label = comment.deleted
    ? 'Deleted comment'
    : `Comment by ${comment.author?.username ?? '[deleted]'}`;

  function startReply() {
    if (state.status !== 'authenticated') {
      const redirect = encodeURIComponent(location.pathname + location.search);
      navigate(`/login?redirect=${redirect}`);
      return;
    }
    create.reset();
    setMode('reply');
  }

  return (
    <div>
      <article
        id={`comment-${comment.id}`}
        aria-label={label}
        className={[styles.comment, highlightId === comment.id && styles.highlight]
          .filter(Boolean)
          .join(' ')}
        ref={highlightId === comment.id ? scrollIntoView : undefined}
      >
        <div className={styles.meta}>
          <AuthorName author={comment.author} avatarSize={22} />
          <span className={styles.dot} aria-hidden="true">
            ·
          </span>
          <TimeAgo timestamp={comment.createdAt} />
          {edited && <span>· edited</span>}
        </div>

        {mode === 'edit' ? (
          <CommentForm
            label="Edit your comment"
            submitLabel="Save"
            initial={comment.content ?? ''}
            busy={update.isPending}
            error={update.error}
            onSubmit={async (content) => {
              await update.mutateAsync(content);
              setMode('view');
            }}
            onCancel={() => setMode('view')}
            autoFocus
          />
        ) : comment.deleted ? (
          <p className={`${styles.content} ${styles.deleted}`}>[deleted]</p>
        ) : (
          <p className={styles.content}>{comment.content}</p>
        )}

        {!comment.deleted && mode !== 'edit' && (
          <div className={styles.actions}>
            <LikeButton
              kind="comment"
              id={comment.id}
              count={comment.likeCount}
              liked={likedIds.has(comment.id)}
              size="small"
            />
            <button type="button" className={styles.action} onClick={startReply}>
              Reply
            </button>
            <AskAiButton
              target={{ kind: 'comment', id: comment.id }}
              onReplied={onHighlight}
              size="small"
            />
            {isMine && (
              <>
                <button
                  type="button"
                  className={styles.action}
                  onClick={() => {
                    update.reset();
                    setMode('edit');
                  }}
                >
                  Edit
                </button>
                <button
                  type="button"
                  className={styles.action}
                  onClick={() => {
                    remove.reset();
                    setMode('delete');
                  }}
                >
                  Delete
                </button>
              </>
            )}
          </div>
        )}

        {mode === 'delete' && (
          <div className={styles.confirm} role="group" aria-label="Confirm deletion">
            <span className={styles.question}>Delete this comment? Its replies stay.</span>
            <Button
              variant="danger"
              size="small"
              busy={remove.isPending}
              onClick={() => remove.mutate(undefined, { onSuccess: () => setMode('view') })}
            >
              Delete comment
            </Button>
            <Button variant="secondary" size="small" onClick={() => setMode('view')}>
              Cancel
            </Button>
            {remove.isError && (
              <div style={{ flexBasis: '100%' }}>
                <Alert>{errorMessage(remove.error)}</Alert>
              </div>
            )}
          </div>
        )}

        {mode === 'reply' && (
          <CommentForm
            label={`Reply to ${comment.author?.username ?? 'this comment'}`}
            submitLabel="Post reply"
            placeholder="Write a reply…"
            busy={create.isPending}
            error={create.error}
            onSubmit={async (content) => {
              const reply = await create.mutateAsync({ content, parentId: comment.id });
              setMode('view');
              onHighlight(reply.id);
            }}
            onCancel={() => setMode('view')}
            autoFocus
          />
        )}
      </article>

      {replies.length > 0 && (
        <div
          className={[styles.replies, depth >= MAX_INDENT && styles.flat].filter(Boolean).join(' ')}
        >
          {replies.map((reply) => (
            <CommentItem
              key={reply.comment.id}
              node={reply}
              depth={depth + 1}
              likedIds={likedIds}
              highlightId={highlightId}
              onHighlight={onHighlight}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function scrollIntoView(element: HTMLElement | null) {
  // jsdom, which the tests run in, has no layout and no scrollIntoView.
  element?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
}
