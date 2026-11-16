/**
 * `/`: the latest posts by everyone. Part of the `posts` feature's public API.
 */
import { ButtonLink } from '@/components/Button';
import { PageTitle } from '@/components/PageTitle';
import { EmptyState } from '@/components/States';
import { useAuth } from '@/features/auth';
import { PostList } from './PostList';
import styles from './PostListPage.module.css';

export function PostListPage() {
  const { state } = useAuth();
  const writeLink =
    state.status === 'authenticated' ? '/posts/new' : '/login?redirect=%2Fposts%2Fnew';

  return (
    <div className="reading">
      <PageTitle>Latest posts</PageTitle>
      <header className={styles.hero}>
        <div>
          <p className={styles.eyebrow}>The blog</p>
          <h1 className={styles.title}>Latest posts</h1>
          <p className={styles.lead}>
            Ideas, notes and stories. Every post gets a short summary written by AI.
          </p>
        </div>
      </header>
      <PostList
        empty={
          <EmptyState
            title="No posts yet"
            action={<ButtonLink to={writeLink}>Write the first one</ButtonLink>}
          >
            Be the first to write something.
          </EmptyState>
        }
      />
    </div>
  );
}
