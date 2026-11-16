/**
 * A message box for errors, successes and notices. Shared component.
 *
 * Errors use `role="alert"`, so screen readers announce them as soon as they appear; the other
 * kinds use `role="status"`, which waits until the reader is idle.
 */
import type { ReactNode } from 'react';
import styles from './Alert.module.css';

interface AlertProps {
  tone?: 'error' | 'success' | 'info';
  children: ReactNode;
}

export function Alert({ tone = 'error', children }: AlertProps) {
  return (
    <div className={`${styles.alert} ${styles[tone]}`} role={tone === 'error' ? 'alert' : 'status'}>
      <div className={styles.body}>{children}</div>
    </div>
  );
}
