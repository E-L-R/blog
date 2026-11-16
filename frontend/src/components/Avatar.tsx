/**
 * A round badge with a user's initial, in a colour picked from their username, so the same
 * user always gets the same colour. Shared component.
 */
import type { Author } from '@/api/types';
import styles from './Avatar.module.css';

const HUES = [12, 32, 145, 175, 200, 225, 260, 290, 320, 345];

interface AvatarProps {
  /** `null` for a deleted account. */
  author: Pick<Author, 'username' | 'bot'> | null;
  size?: number;
}

export function Avatar({ author, size = 28 }: AvatarProps) {
  const style = { width: size, height: size, fontSize: size * 0.42 };
  if (author === null) {
    return (
      <span className={`${styles.avatar} ${styles.deleted}`} style={style} aria-hidden="true" />
    );
  }
  if (author.bot) {
    return (
      <span className={`${styles.avatar} ${styles.bot}`} style={style} aria-hidden="true">
        ✦
      </span>
    );
  }
  const hue = HUES[hash(author.username.toLowerCase()) % HUES.length];
  return (
    <span
      className={styles.avatar}
      style={{ ...style, background: `oklch(0.62 0.13 ${hue})` }}
      aria-hidden="true"
    >
      {author.username.charAt(0).toUpperCase()}
    </span>
  );
}

function hash(text: string): number {
  let value = 0;
  for (let i = 0; i < text.length; i++) value = (value * 31 + text.charCodeAt(i)) >>> 0;
  return value;
}
