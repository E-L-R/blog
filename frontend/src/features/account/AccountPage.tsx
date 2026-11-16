/**
 * `/account`: the current user's account, their posts, and account deletion. Needs a
 * logged-in user; the route table wraps it in `RequireAuth`. Part of the `account` feature's
 * public API.
 */
import { Avatar } from '@/components/Avatar';
import { ButtonLink } from '@/components/Button';
import { PageTitle } from '@/components/PageTitle';
import { EmptyState, Loading } from '@/components/States';
import { useAuth } from '@/features/auth';
import { PostList } from '@/features/posts';
import { formatDate } from '@/lib/time';
import { DeleteAccountForm } from './DeleteAccountForm';
import styles from './Profile.module.css';

export function AccountPage() {
  const { state } = useAuth();
  if (state.status !== 'authenticated') return <Loading />;
  const { me } = state;

  return (
    <div className={`reading ${styles.page}`}>
      <PageTitle>Your account</PageTitle>
      <header className={styles.profile}>
        <Avatar author={{ username: me.username, bot: false }} size={64} />
        <div>
          <h1 className={styles.name}>{me.username}</h1>
          <p className={styles.details}>
            <span>{me.email}</span>
            <span>Joined {formatDate(me.createdAt)}</span>
          </p>
        </div>
      </header>

      <section className={styles.section} aria-labelledby="your-posts">
        <h2 id="your-posts" className={styles.sectionTitle}>
          Your posts
        </h2>
        <PostList
          authorId={me.id}
          showAuthor={false}
          empty={
            <EmptyState
              title="Nothing here yet"
              action={<ButtonLink to="/posts/new">Write your first post</ButtonLink>}
            >
              Posts you write appear here.
            </EmptyState>
          }
        />
      </section>

      <DeleteAccountForm />
    </div>
  );
}
