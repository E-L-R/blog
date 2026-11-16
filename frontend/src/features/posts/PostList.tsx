/**
 * A paged list of posts, newest first: everyone's, or one author's. Part of the `posts`
 * feature's public API, so profile pages can list a user's posts.
 *
 * The page number comes from the URL (`?page=`).
 */
import type { ReactNode } from 'react';
import type { Id } from '@/api/types';
import { Pagination } from '@/components/Pagination';
import { ListSkeleton, QueryError } from '@/components/States';
import { usePageParam } from '@/lib/usePageParam';
import { PostCard } from './PostCard';
import { usePostList } from './queries';

interface PostListProps {
  authorId?: Id;
  /** Shown when there are no posts. */
  empty: ReactNode;
  /** One author's list doesn't need to repeat the author on every card. */
  showAuthor?: boolean;
}

export function PostList({ authorId, empty, showAuthor = true }: PostListProps) {
  const page = usePageParam();
  const query = usePostList({ page, authorId });

  if (query.isPending) return <ListSkeleton />;
  if (query.isError) return <QueryError error={query.error} onRetry={() => void query.refetch()} />;

  const { content, page: info } = query.data;
  if (content.length === 0 && info.number === 0) return <>{empty}</>;

  return (
    <div aria-busy={query.isPlaceholderData || undefined}>
      {content.map((post) => (
        <PostCard key={post.id} post={post} showAuthor={showAuthor} />
      ))}
      <Pagination page={info.number} totalPages={info.totalPages} />
    </div>
  );
}
