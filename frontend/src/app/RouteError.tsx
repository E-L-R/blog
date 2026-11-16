/**
 * Shown when rendering a page throws: a bug, not a failed request (pages show those
 * themselves). The error goes to the console, never to the screen.
 */
import { useEffect } from 'react';
import { useRouteError } from 'react-router';
import { Button } from '@/components/Button';
import { EmptyState } from '@/components/States';

export function RouteError() {
  const error = useRouteError();
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="reading" style={{ padding: 'var(--space-8) var(--gutter)' }}>
      <EmptyState
        title="Something went wrong"
        action={<Button onClick={() => window.location.assign('/')}>Reload the app</Button>}
      >
        An unexpected error stopped this page from loading.
      </EmptyState>
    </div>
  );
}
