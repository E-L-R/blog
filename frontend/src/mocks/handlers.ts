/**
 * The mock API: MSW request handlers that answer like the backend, following docs/API.md.
 * The same handlers serve the tests (server.ts) and mock mode in the browser (browser.ts).
 *
 * Where the design and the backend differ today, the handlers follow the design, and
 * docs/FRONTEND.md#mock-api says so.
 */
import { http, HttpResponse, type HttpResponseResolver } from 'msw';
import type { FieldError, ProblemDetail } from '@/api/types';
import { MockDb, type MockUser } from './db';

const TOKEN_PREFIX = 'mock-token.';
const TOKEN_TTL_SECONDS = 3600;

/** Matches the API under /api, on any origin. */
const api = (path: string) => `*/api${path}`;

export function createHandlers(db: MockDb, onChange: () => void = () => {}) {
  /** Wraps a handler that changes data, so the browser can save the database afterwards. */
  function writes<R extends HttpResponseResolver>(resolver: R): R {
    return (async (info: Parameters<R>[0]) => {
      const response = await resolver(info);
      onChange();
      return response;
    }) as R;
  }

  /** The account the request's token belongs to, or `undefined` if it has no valid token. */
  function currentUser(request: Request): MockUser | undefined {
    const header = request.headers.get('Authorization');
    if (!header?.startsWith(`Bearer ${TOKEN_PREFIX}`)) return undefined;
    const user = db.findUser(header.slice(`Bearer ${TOKEN_PREFIX}`.length));
    return user && !user.deleted ? user : undefined;
  }

  /**
   * A request with a token that doesn't work gets 401 even on a public endpoint
   * (docs/API.md#authentication).
   */
  function rejectsToken(request: Request): boolean {
    return request.headers.has('Authorization') && currentUser(request) === undefined;
  }

  return [
    http.post(
      api('/auth/register'),
      writes(async ({ request }) => {
        const body = await readJson(request);
        if (body === undefined)
          return problem(request, 400, 'BAD_REQUEST', 'Failed to read request');
        const email = text(body.email);
        const username = text(body.username);
        const password = text(body.password);
        const errors = validate({
          email: [
            [email.trim() === '', 'must not be blank'],
            [
              email !== '' && !/^[^\s@]+@[^\s@]+$/.test(email),
              'must be a well-formed email address',
            ],
            [email.length > 254, 'size must be between 0 and 254'],
          ],
          username: [
            [
              !/^[A-Za-z0-9_-]{3,30}$/.test(username),
              'must be 3 to 30 characters: letters, digits, _ and -',
            ],
          ],
          password: [
            [password.length < 8, 'must be at least 8 characters'],
            [new TextEncoder().encode(password).length > 72, 'must be at most 72 bytes'],
          ],
        });
        if (errors.length > 0) return validationProblem(request, errors);
        if (db.emailTaken(email)) {
          return problem(request, 409, 'EMAIL_TAKEN', 'This email is already registered.');
        }
        if (db.usernameTaken(username)) {
          return problem(request, 409, 'USERNAME_TAKEN', 'This username is taken.');
        }
        const user = db.createUser(email, username, password);
        return HttpResponse.json(MockDb.meView(user), { status: 201 });
      }),
    ),

    http.post(api('/auth/login'), async ({ request }) => {
      const body = await readJson(request);
      if (body === undefined) return problem(request, 400, 'BAD_REQUEST', 'Failed to read request');
      const email = text(body.email);
      const password = text(body.password);
      const errors = validate({
        email: [[email.trim() === '', 'must not be blank']],
        password: [[password === '', 'must not be blank']],
      });
      if (errors.length > 0) return validationProblem(request, errors);
      const user = db.authenticate(email, password);
      if (!user) return problem(request, 401, 'INVALID_CREDENTIALS', 'Wrong email or password.');
      return HttpResponse.json({
        accessToken: TOKEN_PREFIX + user.id,
        tokenType: 'Bearer',
        expiresIn: TOKEN_TTL_SECONDS,
      });
    }),

    http.get(api('/me'), ({ request }) => {
      const user = currentUser(request);
      if (!user) return unauthenticated(request);
      return HttpResponse.json(MockDb.meView(user));
    }),

    http.post(
      api('/me/delete'),
      writes(async ({ request }) => {
        const user = currentUser(request);
        if (!user) return unauthenticated(request);
        const body = await readJson(request);
        const password = text(body?.password);
        if (password === '') {
          return validationProblem(request, [{ field: 'password', message: 'must not be blank' }]);
        }
        if (user.password !== password) {
          return problem(request, 403, 'INVALID_PASSWORD', 'The password is wrong.');
        }
        db.deleteUser(user);
        return new HttpResponse(null, { status: 204 });
      }),
    ),

    http.get(api('/users/:id'), ({ request, params }) => {
      if (rejectsToken(request)) return unauthenticated(request);
      const user = db.findUser(String(params.id));
      if (!user || user.deleted) return notFound(request);
      return HttpResponse.json(MockDb.userView(user));
    }),

    http.get(api('/posts'), ({ request }) => {
      if (rejectsToken(request)) return unauthenticated(request);
      const url = new URL(request.url);
      const page = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 50) {
        return problem(request, 400, 'VALIDATION_FAILED', 'The request is invalid.');
      }
      const posts = db.listPosts(url.searchParams.get('authorId') ?? undefined);
      return HttpResponse.json({
        content: posts
          .slice(page * size, (page + 1) * size)
          .map((post) => db.postSummaryView(post)),
        page: {
          number: page,
          size,
          totalElements: posts.length,
          totalPages: Math.ceil(posts.length / size),
        },
      });
    }),

    http.get(api('/posts/:id'), ({ request, params }) => {
      if (rejectsToken(request)) return unauthenticated(request);
      const post = db.findPost(String(params.id));
      if (!post) return notFound(request);
      return HttpResponse.json(db.postView(post));
    }),

    http.post(
      api('/posts'),
      writes(async ({ request }) => {
        const user = currentUser(request);
        if (!user) return unauthenticated(request);
        const body = await readJson(request);
        if (body === undefined)
          return problem(request, 400, 'BAD_REQUEST', 'Failed to read request');
        const errors = validatePost(body);
        if (errors.length > 0) return validationProblem(request, errors);
        const post = db.createPost(user.id, text(body.title), text(body.content));
        return HttpResponse.json(db.postView(post), {
          status: 201,
          headers: { Location: `/posts/${post.id}` },
        });
      }),
    ),

    http.put(
      api('/posts/:id'),
      writes(async ({ request, params }) => {
        const user = currentUser(request);
        if (!user) return unauthenticated(request);
        const post = db.findPost(String(params.id));
        if (!post) return notFound(request);
        const body = await readJson(request);
        if (body === undefined)
          return problem(request, 400, 'BAD_REQUEST', 'Failed to read request');
        const errors = validatePost(body);
        if (errors.length > 0) return validationProblem(request, errors);
        if (post.authorId !== user.id) {
          return problem(request, 403, 'NOT_AUTHOR', 'Only the author can change this post.');
        }
        db.editPost(post, text(body.title), text(body.content));
        return HttpResponse.json(db.postView(post));
      }),
    ),

    http.delete(
      api('/posts/:id'),
      writes(({ request, params }) => {
        const user = currentUser(request);
        if (!user) return unauthenticated(request);
        const post = db.findPost(String(params.id));
        if (!post) return notFound(request);
        if (post.authorId !== user.id) {
          return problem(request, 403, 'NOT_AUTHOR', 'Only the author can delete this post.');
        }
        db.deletePost(post);
        return new HttpResponse(null, { status: 204 });
      }),
    ),
  ];
}

// ---- helpers ----

type Body = Record<string, unknown>;

async function readJson(request: Request): Promise<Body | undefined> {
  try {
    const body: unknown = await request.json();
    return typeof body === 'object' && body !== null ? (body as Body) : undefined;
  } catch {
    return undefined;
  }
}

function text(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

/** Each field's rules as [broken, message] pairs; returns the broken ones, sorted by field. */
function validate(rules: Record<string, [boolean, string][]>): FieldError[] {
  return Object.entries(rules)
    .flatMap(([field, checks]) =>
      checks.filter(([broken]) => broken).map(([, message]) => ({ field, message })),
    )
    .sort((a, b) => a.field.localeCompare(b.field));
}

function validatePost(body: Body): FieldError[] {
  const title = text(body.title);
  const content = text(body.content);
  return validate({
    title: [
      [title.trim() === '', 'must not be blank'],
      [title.length > 200, 'size must be between 0 and 200'],
    ],
    content: [
      [content.trim() === '', 'must not be blank'],
      [content.length > 50_000, 'size must be between 0 and 50000'],
    ],
  });
}

const TITLES: Record<number, string> = {
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
};

export function problem(
  request: Request,
  status: number,
  code: string,
  detail: string,
  errors?: FieldError[],
) {
  const body: ProblemDetail = {
    title: TITLES[status] ?? 'Error',
    status,
    detail,
    instance: new URL(request.url).pathname.replace(/^\/api/, ''),
    code,
    ...(errors ? { errors } : {}),
  };
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function validationProblem(request: Request, errors: FieldError[]) {
  return problem(request, 400, 'VALIDATION_FAILED', 'The request is invalid.', errors);
}

function unauthenticated(request: Request) {
  return problem(request, 401, 'UNAUTHENTICATED', 'Log in to do this.');
}

function notFound(request: Request) {
  return problem(request, 404, 'NOT_FOUND', 'Not found.');
}
