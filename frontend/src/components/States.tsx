/**
 * Placeholders for the states a page passes through besides "loaded": loading, empty and
 * failed. Shared components.
 */
import type { ReactNode } from 'react';
import { errorMessage } from '@/lib/errors';
import { Alert } from './Alert';
import { Button } from './Button';
import styles from './States.module.css';

export function EmptyState({
  title,
  children,
  action,
}: {
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className={styles.empty}>
      <h2 className={styles.emptyTitle}>{title}</h2>
      {children && <p className={styles.emptyText}>{children}</p>}
      {action}
    </div>
  );
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className={styles.loading} role="status">
      {label}
    </div>
  );
}

/** Grey bars in the shape of a list of posts, shown while the first page loads. */
export function ListSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Loading posts">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className={styles.skeleton} aria-hidden="true">
          <div className={`${styles.bar} ${styles.barTitle}`} />
          <div className={styles.bar} />
          <div className={styles.bar} style={{ width: '40%' }} />
        </div>
      ))}
    </div>
  );
}

/** An error message for a failed query, with a button to try again. */
export function QueryError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <Alert>
      <p>{errorMessage(error)}</p>
      {onRetry && (
        <p style={{ marginTop: 'var(--space-2)' }}>
          <Button variant="secondary" size="small" onClick={onRetry}>
            Try again
          </Button>
        </p>
      )}
    </Alert>
  );
}
