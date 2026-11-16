/**
 * An author's avatar and name, linking to their profile. Shared component.
 *
 * A deleted account shows as "[deleted]", without a link (docs/API.md#resources), and the AI
 * bot gets a badge, so its writing is never mistaken for a person's.
 */
import { Link } from 'react-router';
import type { Author } from '@/api/types';
import { Avatar } from './Avatar';
import styles from './AuthorName.module.css';

export const DELETED_AUTHOR = '[deleted]';

export function AuthorName({ author, avatarSize }: { author: Author | null; avatarSize?: number }) {
  return (
    <span className={styles.author}>
      <Avatar author={author} size={avatarSize} />
      {author === null ? (
        <span className={`${styles.name} ${styles.deleted}`}>{DELETED_AUTHOR}</span>
      ) : (
        <>
          <Link to={`/users/${author.id}`} className={styles.name}>
            {author.username}
          </Link>
          {author.bot && <span className={styles.badge}>AI</span>}
        </>
      )}
    </span>
  );
}
