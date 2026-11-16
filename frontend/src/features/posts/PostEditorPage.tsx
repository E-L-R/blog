/**
 * `/posts/new` and `/posts/:postId/edit`. Both need a logged-in user; the route table wraps
 * them in `RequireAuth`. Part of the `posts` feature's public API.
 */
import { useNavigate, useParams } from 'react-router';
import { ButtonLink } from '@/components/Button';
import { PageTitle } from '@/components/PageTitle';
import { EmptyState, Loading, QueryError } from '@/components/States';
import { useAuth } from '@/features/auth';
import { isNotFound } from '@/lib/errors';
import { PostForm } from './PostForm';
import { useCreatePost, usePost, useUpdatePost } from './queries';
import styles from './PostEditorPage.module.css';

export function NewPostPage() {
  const navigate = useNavigate();
  const mutation = useCreatePost();

  return (
    <div className={`reading ${styles.page}`}>
      <PageTitle>New post</PageTitle>
      <h1 className={styles.heading}>New post</h1>
      <PostForm
        submitLabel="Publish"
        cancelTo="/"
        busy={mutation.isPending}
        error={mutation.error}
        onSubmit={(input) =>
          mutation.mutate(input, { onSuccess: (post) => navigate(`/posts/${post.id}`) })
        }
      />
    </div>
  );
}

export function EditPostPage() {
  const { postId = '' } = useParams();
  const navigate = useNavigate();
  const { state } = useAuth();
  const query = usePost(postId);
  const mutation = useUpdatePost(postId);

  if (query.isPending) return <Loading label="Loading post…" />;
  if (query.isError) {
    return (
      <div className={`reading ${styles.page}`}>
        {isNotFound(query.error) ? (
          <EmptyState title="Post not found">
            This post doesn't exist, or it was deleted.
          </EmptyState>
        ) : (
          <QueryError error={query.error} onRetry={() => void query.refetch()} />
        )}
      </div>
    );
  }

  const post = query.data;
  // The API refuses the edit anyway (403 NOT_AUTHOR); this saves the user typing in vain.
  if (state.status === 'authenticated' && post.author?.id !== state.me.id) {
    return (
      <div className={`reading ${styles.page}`}>
        <EmptyState
          title="You can't edit this post"
          action={<ButtonLink to={`/posts/${post.id}`}>Back to the post</ButtonLink>}
        >
          Only its author can edit a post.
        </EmptyState>
      </div>
    );
  }

  return (
    <div className={`reading ${styles.page}`}>
      <PageTitle>{`Edit: ${post.title}`}</PageTitle>
      <h1 className={styles.heading}>Edit post</h1>
      <PostForm
        key={post.id}
        initial={{ title: post.title, content: post.content }}
        submitLabel="Save changes"
        cancelTo={`/posts/${post.id}`}
        busy={mutation.isPending}
        error={mutation.error}
        onSubmit={(input) =>
          mutation.mutate(input, { onSuccess: () => navigate(`/posts/${post.id}`) })
        }
      />
    </div>
  );
}
