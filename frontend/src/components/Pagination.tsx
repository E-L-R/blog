/**
 * "Newer" and "Older" links for a paged list. Shared component.
 *
 * The page lives in the URL (`?page=2`), so the back button, reloading and sharing a link
 * all keep it. The URL counts pages from 1 for people; the API counts from 0.
 */
import { useSearchParams } from 'react-router';
import { ButtonLink } from './Button';
import styles from './Pagination.module.css';

export function Pagination({ page, totalPages }: { page: number; totalPages: number }) {
  const [searchParams] = useSearchParams();
  if (totalPages <= 1) return null;

  const linkTo = (target: number) => {
    const params = new URLSearchParams(searchParams);
    if (target === 0) params.delete('page');
    else params.set('page', String(target + 1));
    const query = params.toString();
    return query === '' ? '.' : `?${query}`;
  };

  return (
    <nav className={styles.pagination} aria-label="Pages">
      {page > 0 ? (
        <ButtonLink to={linkTo(page - 1)} variant="secondary" size="small">
          ← Newer
        </ButtonLink>
      ) : (
        <span className={styles.placeholder} />
      )}
      <span className={styles.status}>
        Page {page + 1} of {totalPages}
      </span>
      {page < totalPages - 1 ? (
        <ButtonLink to={linkTo(page + 1)} variant="secondary" size="small">
          Older →
        </ButtonLink>
      ) : (
        <span className={styles.placeholder} />
      )}
    </nav>
  );
}
