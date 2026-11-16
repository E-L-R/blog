import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { AiRequest } from '@/api/types';
import { server } from '@/mocks/server';
import { createLoggedInUser } from '@/test/render';
import { askAndWait, GIVE_UP_AFTER_MS, POLL_INTERVAL_MS } from './waitForReply';

function request(status: AiRequest['status']): AiRequest {
  return {
    id: 'req',
    status,
    postId: 'post',
    commentId: null,
    resultCommentId: status === 'COMPLETED' ? 'reply' : null,
    createdAt: '2026-11-16T12:00:00Z',
    completedAt: null,
  };
}

/** Answers `POST .../ai-reply` with PENDING, then each poll with the next status. */
function answer(...statuses: AiRequest['status'][]) {
  let polls = 0;
  server.use(
    http.post('*/api/posts/:id/ai-reply', () =>
      HttpResponse.json(request('PENDING'), { status: 202 }),
    ),
    http.get('*/api/ai-requests/:id', () => {
      const status = statuses[Math.min(polls, statuses.length - 1)]!;
      polls++;
      return HttpResponse.json(request(status));
    }),
  );
  return () => polls;
}

describe('askAndWait', () => {
  it('asks until the request is final, waiting between polls', async () => {
    createLoggedInUser();
    const polls = answer('PENDING', 'IN_PROGRESS', 'COMPLETED');
    const waits: number[] = [];

    const result = await askAndWait({ kind: 'post', id: 'post' }, Date.now, async (ms) => {
      waits.push(ms);
    });

    expect(result.status).toBe('COMPLETED');
    expect(result.resultCommentId).toBe('reply');
    expect(polls()).toBe(3);
    expect(waits).toEqual([POLL_INTERVAL_MS, POLL_INTERVAL_MS]);
  });

  it('stops at a failed request', async () => {
    createLoggedInUser();
    answer('FAILED');

    const result = await askAndWait({ kind: 'post', id: 'post' }, Date.now, async () => {});

    expect(result.status).toBe('FAILED');
  });

  it('gives up after a while, with the request still pending', async () => {
    createLoggedInUser();
    const polls = answer('PENDING');
    let now = 0;

    const result = await askAndWait(
      { kind: 'post', id: 'post' },
      () => now,
      async (ms) => {
        now += ms;
      },
    );

    expect(result.status).toBe('PENDING');
    expect(polls()).toBe(Math.floor(GIVE_UP_AFTER_MS / POLL_INTERVAL_MS) + 1);
  });
});
