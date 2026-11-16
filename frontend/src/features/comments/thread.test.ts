import { describe, expect, it } from 'vitest';
import type { Comment } from '@/api/types';
import { buildThread, countVisible } from './thread';

function comment(id: string, parentId: string | null, deleted = false): Comment {
  return {
    id,
    postId: 'post',
    parentId,
    author: deleted ? null : { id: 'u', username: 'u', bot: false },
    content: deleted ? null : id,
    deleted,
    likeCount: 0,
    createdAt: '2026-11-16T12:00:00Z',
    updatedAt: '2026-11-16T12:00:00Z',
  };
}

describe('buildThread', () => {
  it('puts each reply under its parent, keeping the order at every level', () => {
    const thread = buildThread([
      comment('a', null),
      comment('b', null),
      comment('a1', 'a'),
      comment('a1x', 'a1'),
      comment('a2', 'a'),
    ]);

    expect(thread.map((node) => node.comment.id)).toEqual(['a', 'b']);
    expect(thread[0]!.replies.map((node) => node.comment.id)).toEqual(['a1', 'a2']);
    expect(thread[0]!.replies[0]!.replies.map((node) => node.comment.id)).toEqual(['a1x']);
    expect(thread[1]!.replies).toEqual([]);
  });

  it('keeps a comment whose parent is missing, at the top level', () => {
    const thread = buildThread([comment('orphan', 'gone')]);

    expect(thread.map((node) => node.comment.id)).toEqual(['orphan']);
  });

  it('keeps deleted comments, so their replies stay in place', () => {
    const thread = buildThread([comment('a', null, true), comment('a1', 'a')]);

    expect(thread[0]!.comment.deleted).toBe(true);
    expect(thread[0]!.replies.map((node) => node.comment.id)).toEqual(['a1']);
  });

  it('counts only comments that are not deleted', () => {
    expect(countVisible([comment('a', null, true), comment('b', null), comment('c', 'b')])).toBe(2);
  });
});
