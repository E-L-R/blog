/** Any URL that matches no route. */
import { ButtonLink } from '@/components/Button';
import { PageTitle } from '@/components/PageTitle';
import { EmptyState } from '@/components/States';

export function NotFoundPage() {
  return (
    <div className="reading" style={{ paddingBlock: 'var(--space-8)' }}>
      <PageTitle>Page not found</PageTitle>
      <EmptyState
        title="Page not found"
        action={<ButtonLink to="/">Go to the home page</ButtonLink>}
      >
        There's nothing at this address.
      </EmptyState>
    </div>
  );
}
