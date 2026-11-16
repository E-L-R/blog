/**
 * `/users/:userId`: a user's public profile and their posts. Part of the `account` feature's
 * public API.
 */
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { Avatar } from '@/components/Avatar';
import { ButtonLink } from '@/components/Button';
import { PageTitle } from '@/components/PageTitle';
import { EmptyState, Loading, QueryError } from '@/components/States';
import { PostList } from '@/features/posts';
import { isNotFound } from '@/lib/errors';
import { formatDate } from '@/lib/time';
import { getUser } from './api';
import styles from './Profile.module.css';

export function UserPage() {
  const { userId = '' } = useParams();
  const query = useQuery({
    queryKey: ['users', userId],
    queryFn: ({ signal }) => getUser(userId, signal),
  });

  if (query.isPending) return <Loading />;
  if (query.isError) {
    return (
      <div className={`reading ${styles.page}`}>
        {isNotFound(query.error) ? (
          <>
            <PageTitle>User not found</PageTitle>
            <EmptyState
              title="User not found"
              action={<ButtonLink to="/">See all posts</ButtonLink>}
            >
              This account doesn't exist, or it was deleted.
            </EmptyState>
          </>
        ) : (
          <QueryError error={query.error} onRetry={() => void query.refetch()} />
        )}
      </div>
    );
  }

  const user = query.data;
  return (
    <div className={`reading ${styles.page}`}>
      <PageTitle>{user.username}</PageTitle>
      <header className={styles.profile}>
        <Avatar author={user} size={64} />
        <div>
          <h1 className={styles.name}>{user.username}</h1>
          <p className={styles.details}>
            {user.bot && <span className={styles.badge}>AI assistant</span>}
            <span>Joined {formatDate(user.createdAt)}</span>
          </p>
        </div>
      </header>
      <section className={styles.section} aria-labelledby="user-posts">
        <h2 id="user-posts" className={styles.sectionTitle}>
          Posts
        </h2>
        <PostList
          authorId={user.id}
          showAuthor={false}
          empty={
            <EmptyState title="No posts yet">
              {user.username} hasn't written anything yet.
            </EmptyState>
          }
        />
      </section>
    </div>
  );
}
