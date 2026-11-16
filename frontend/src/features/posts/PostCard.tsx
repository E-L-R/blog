/**
 * One post in a list: its title, its TL;DR as a summary, its author and age. Internal to
 * `posts`.
 */
import { Link } from 'react-router';
import type { PostSummary } from '@/api/types';
import { AuthorName } from '@/components/AuthorName';
import { TimeAgo } from '@/components/TimeAgo';
import styles from './PostCard.module.css';

export function PostCard({ post, showAuthor = true }: { post: PostSummary; showAuthor?: boolean }) {
  return (
    <article className={styles.card}>
      <h2 className={styles.title}>
        <Link to={`/posts/${post.id}`} className={styles.link}>
          {post.title}
        </Link>
      </h2>
      {post.tldr && (
        <p className={styles.summary}>
          <span className={styles.tag}>TL;DR</span>
          {post.tldr}
        </p>
      )}
      <div className={styles.meta}>
        {showAuthor && (
          <>
            <AuthorName author={post.author} avatarSize={22} />
            <span className={styles.dot} aria-hidden="true">
              ·
            </span>
          </>
        )}
        <TimeAgo timestamp={post.createdAt} />
      </div>
    </article>
  );
}
