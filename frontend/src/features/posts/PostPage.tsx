/**
 * `/posts/:postId`: one post, with its TL;DR, its likes and its comments. Its author also gets
 * Edit and Delete. Part of the `posts` feature's public API.
 */
import { Link, useParams } from 'react-router';
import { AuthorName } from '@/components/AuthorName';
import { ButtonLink } from '@/components/Button';
import { PageTitle } from '@/components/PageTitle';
import { EmptyState, Loading, QueryError } from '@/components/States';
import { TimeAgo } from '@/components/TimeAgo';
import { useAuth } from '@/features/auth';
import { CommentSection } from '@/features/comments';
import { LikeButton, useLikedIds } from '@/features/likes';
import { isNotFound } from '@/lib/errors';
import { DeletePostButton } from './DeletePostButton';
import { usePost } from './queries';
import { Tldr } from './Tldr';
import styles from './PostPage.module.css';

export function PostPage() {
  const { postId = '' } = useParams();
  const { state } = useAuth();
  const query = usePost(postId);
  const likedIds = useLikedIds('post', query.data ? [query.data.id] : []);

  if (query.isPending) return <Loading label="Loading post…" />;
  if (query.isError) {
    if (isNotFound(query.error)) return <PostNotFound />;
    return (
      <div className={`reading ${styles.article}`}>
        <QueryError error={query.error} onRetry={() => void query.refetch()} />
      </div>
    );
  }

  const post = query.data;
  const isAuthor = state.status === 'authenticated' && post.author?.id === state.me.id;
  const edited = post.updatedAt !== post.createdAt;

  return (
    <article className={`reading ${styles.article}`}>
      <PageTitle>{post.title}</PageTitle>
      <Link to="/" className={styles.back}>
        ← All posts
      </Link>
      <header className={styles.header}>
        <h1 className={styles.title}>{post.title}</h1>
        <div className={styles.meta}>
          <AuthorName author={post.author} />
          <span className={styles.dot} aria-hidden="true">
            ·
          </span>
          <TimeAgo timestamp={post.createdAt} />
          {edited && (
            <>
              <span className={styles.dot} aria-hidden="true">
                ·
              </span>
              <span>
                edited <TimeAgo timestamp={post.updatedAt} />
              </span>
            </>
          )}
          {isAuthor && (
            <div className={styles.actions}>
              <ButtonLink to={`/posts/${post.id}/edit`} variant="secondary" size="small">
                Edit
              </ButtonLink>
              <DeletePostButton postId={post.id} />
            </div>
          )}
        </div>
      </header>
      <Tldr post={post} />
      <div className={styles.content}>{post.content}</div>
      {post.likeCount !== undefined && (
        <div className={styles.footer}>
          <LikeButton
            kind="post"
            id={post.id}
            count={post.likeCount}
            liked={likedIds.has(post.id)}
          />
        </div>
      )}
      <CommentSection postId={post.id} />
    </article>
  );
}

function PostNotFound() {
  return (
    <div className={`reading ${styles.article}`}>
      <PageTitle>Post not found</PageTitle>
      <EmptyState title="Post not found" action={<ButtonLink to="/">See all posts</ButtonLink>}>
        This post doesn't exist, or its author deleted it.
      </EmptyState>
    </div>
  );
}
