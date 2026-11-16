/**
 * The page of a paged list, read from the URL. Pages in the URL count from 1 for people
 * (`?page=2`); the API counts from 0. A missing or invalid value means the first page.
 */
import { useSearchParams } from 'react-router';

export function usePageParam(): number {
  const [searchParams] = useSearchParams();
  const value = Number(searchParams.get('page'));
  return Number.isInteger(value) && value > 1 ? value - 1 : 0;
}
