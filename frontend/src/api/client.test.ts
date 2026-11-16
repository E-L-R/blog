import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '@/mocks/server';
import { ApiError, apiRequest } from './client';
import { tokenStore } from './token';

describe('apiRequest', () => {
  it('sends the token, the JSON body and the query parameters', async () => {
    tokenStore.set('secret', 60);
    let seen: Request | undefined;
    server.use(
      http.post('*/api/things', async ({ request }) => {
        seen = request.clone();
        return HttpResponse.json({ ok: true }, { status: 201 });
      }),
    );

    const result = await apiRequest('/things', {
      method: 'POST',
      body: { name: 'x' },
      query: { page: 2, skipped: undefined },
    });

    expect(result).toEqual({ ok: true });
    expect(seen?.headers.get('Authorization')).toBe('Bearer secret');
    expect(seen?.headers.get('Content-Type')).toBe('application/json');
    expect(await seen?.json()).toEqual({ name: 'x' });
    const url = new URL(seen!.url);
    expect(url.pathname).toBe('/api/things');
    expect(url.search).toBe('?page=2');
  });

  it('sends no Authorization header without a token', async () => {
    let seen: Request | undefined;
    server.use(
      http.get('*/api/things', ({ request }) => {
        seen = request;
        return HttpResponse.json([]);
      }),
    );

    await apiRequest('/things');

    expect(seen?.headers.has('Authorization')).toBe(false);
  });

  it('returns undefined for 204 No Content', async () => {
    server.use(http.delete('*/api/things/1', () => new HttpResponse(null, { status: 204 })));

    await expect(apiRequest('/things/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('turns Problem Details into an ApiError with the code and field errors', async () => {
    server.use(
      http.post('*/api/things', () =>
        HttpResponse.json(
          {
            title: 'Bad Request',
            status: 400,
            detail: 'The request is invalid.',
            code: 'VALIDATION_FAILED',
            errors: [{ field: 'title', message: 'must not be blank' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const error = await apiRequest('/things', { method: 'POST', body: {} }).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'title', message: 'must not be blank' }],
    });
  });

  it('reports a 401 without a body as UNAUTHENTICATED', async () => {
    server.use(http.get('*/api/things', () => new HttpResponse(null, { status: 401 })));

    await expect(apiRequest('/things')).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHENTICATED',
    });
  });

  it('reports a response that is not Problem Details as UNKNOWN', async () => {
    server.use(
      http.get('*/api/things', () => new HttpResponse('<html>Bad gateway</html>', { status: 502 })),
    );

    await expect(apiRequest('/things')).rejects.toMatchObject({ status: 502, code: 'UNKNOWN' });
  });

  it('forgets a token the API refuses, and loads a public page without it', async () => {
    tokenStore.set('stale', 60);
    server.use(
      http.get('*/api/things', ({ request }) =>
        request.headers.has('Authorization')
          ? new HttpResponse(null, { status: 401 })
          : HttpResponse.json(['public']),
      ),
    );

    await expect(apiRequest('/things')).resolves.toEqual(['public']);
    expect(tokenStore.get()).toBeNull();
  });

  it('does not send a refused write again', async () => {
    tokenStore.set('stale', 60);
    let calls = 0;
    server.use(
      http.post('*/api/things', () => {
        calls++;
        return new HttpResponse(null, { status: 401 });
      }),
    );

    await expect(apiRequest('/things', { method: 'POST', body: {} })).rejects.toMatchObject({
      code: 'UNAUTHENTICATED',
    });
    expect(calls).toBe(1);
    expect(tokenStore.get()).toBeNull();
  });

  it('reports a request that got no response as NETWORK_ERROR', async () => {
    server.use(http.get('*/api/things', () => HttpResponse.error()));

    await expect(apiRequest('/things')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });
});
