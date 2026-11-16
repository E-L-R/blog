/**
 * A post's AI-written TL;DR, in each of its states (docs/DOMAIN.md#post). Internal to `posts`.
 *
 * - `PENDING` without a TL;DR: a new post; the summary is being written.
 * - `PENDING` with a TL;DR: the content changed, and this is the previous version's summary.
 * - `GENERATED`: the summary matches the content.
 * - `FAILED`: no summary could be written.
 *
 * Before the backend's `ai` step, posts have no TL;DR fields, and nothing is shown.
 */
import type { Post } from '@/api/types';
import styles from './Tldr.module.css';

export function Tldr({ post }: { post: Pick<Post, 'tldr' | 'tldrStatus'> }) {
  const { tldr, tldrStatus } = post;
  if (tldrStatus === undefined) return null;

  return (
    <aside className={styles.tldr} aria-label="AI summary" aria-live="polite">
      <div className={styles.label}>
        <span className={styles.spark} aria-hidden="true">
          ✦
        </span>
        TL;DR by AI
        {tldrStatus === 'PENDING' && tldr && <span className={styles.state}>Updating…</span>}
      </div>
      {tldr ? (
        <p className={styles.text}>{tldr}</p>
      ) : tldrStatus === 'FAILED' ? (
        <p className={styles.muted}>A summary couldn't be written for this post.</p>
      ) : (
        <p className={styles.muted}>
          <span className={styles.writing} aria-hidden="true">
            <span />
            <span />
            <span />
          </span>{' '}
          Writing a summary…
        </p>
      )}
    </aside>
  );
}
