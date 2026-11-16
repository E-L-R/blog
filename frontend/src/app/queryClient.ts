/**
 * The TanStack Query client: the cache for everything loaded from the API.
 */
import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api/client';

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Data is fresh for 30 seconds: going back to a page in that time shows it at once,
        // without asking the server again.
        staleTime: 30_000,
        // A 4xx answer won't change by asking again; a network error or a 5xx might.
        retry: (failureCount, error) =>
          failureCount < 2 &&
          !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
    },
  });
}
