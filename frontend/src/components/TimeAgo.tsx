/**
 * A timestamp shown as "5 minutes ago", with the full date in a tooltip. Shared component.
 */
import { useNow } from '@/lib/useNow';
import { formatDateTime, formatRelative } from '@/lib/time';

export function TimeAgo({ timestamp }: { timestamp: string }) {
  const now = useNow();
  return (
    <time dateTime={timestamp} title={formatDateTime(timestamp)}>
      {formatRelative(timestamp, now)}
    </time>
  );
}
