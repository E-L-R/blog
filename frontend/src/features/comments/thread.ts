/**
 * Builds the tree of a thread from the flat list the API returns (docs/DECISIONS.md, D13).
 * Internal to `comments`.
 */
import type { Comment, Id } from '@/api/types';

export interface ThreadNode {
  comment: Comment;
  replies: ThreadNode[];
}

/**
 * Each comment goes under its parent, keeping the list's order (oldest first) at every level.
 * A comment whose parent isn't in the list is shown at the top level rather than lost.
 */
export function buildThread(comments: readonly Comment[]): ThreadNode[] {
  const nodes = new Map<Id, ThreadNode>();
  for (const comment of comments) nodes.set(comment.id, { comment, replies: [] });

  const roots: ThreadNode[] = [];
  for (const comment of comments) {
    const node = nodes.get(comment.id)!;
    const parent = comment.parentId === null ? undefined : nodes.get(comment.parentId);
    if (parent) parent.replies.push(node);
    else roots.push(node);
  }
  return roots;
}

/** The number of comments that are not deleted. */
export function countVisible(comments: readonly Comment[]): number {
  return comments.filter((comment) => !comment.deleted).length;
}
