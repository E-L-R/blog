/**
 * Deletes a post after the author confirms. The confirmation appears in place instead of in
 * a pop-up, so it needs no focus juggling. Internal to `posts`.
 */
import { useState } from 'react';
import { useNavigate } from 'react-router';
import type { Id } from '@/api/types';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { errorMessage } from '@/lib/errors';
import { useDeletePost } from './queries';
import styles from './DeletePostButton.module.css';

export function DeletePostButton({ postId }: { postId: Id }) {
  const [confirming, setConfirming] = useState(false);
  const navigate = useNavigate();
  const mutation = useDeletePost(postId);

  if (!confirming) {
    return (
      <Button variant="ghost" size="small" onClick={() => setConfirming(true)}>
        Delete
      </Button>
    );
  }

  return (
    <div className={styles.confirm} role="group" aria-label="Confirm deletion">
      <span className={styles.question}>Delete this post? Its comments and likes go with it.</span>
      <Button
        variant="danger"
        size="small"
        busy={mutation.isPending}
        onClick={() =>
          mutation.mutate(undefined, {
            onSuccess: () => navigate('/', { replace: true, state: { notice: 'Post deleted.' } }),
          })
        }
      >
        Delete post
      </Button>
      <Button variant="secondary" size="small" onClick={() => setConfirming(false)}>
        Cancel
      </Button>
      {mutation.isError && (
        <div style={{ flexBasis: '100%' }}>
          <Alert>{errorMessage(mutation.error)}</Alert>
        </div>
      )}
    </div>
  );
}
