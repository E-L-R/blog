# REST API

> **Status:** design. No endpoint exists yet. The `user` and `post` services that the first endpoints will call are built; the endpoints come with the `auth` step. Once the API is built, springdoc will serve the generated OpenAPI description at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`. This document is the design those will be checked against.

The rules behind each endpoint are in [DOMAIN.md](DOMAIN.md).

## Conventions

- **Base URL:** `http://localhost:8080` in local development. Paths have no version prefix; the API may change without notice until version 1.0.
- **Format:** JSON (UTF-8) with camelCase field names. Fields with no value are present with `null`.
- **IDs:** UUID strings.
- **Times:** ISO 8601 in UTC, for example `2026-11-05T12:00:00Z`.
- **Content:** titles, posts and comments are plain text, stored and returned exactly as written. The API never renders or sanitizes HTML, so clients must escape it when displaying it (or sanitize it, if they render Markdown).
- **CORS:** only the origins listed in `blog.cors.allowed-origins` may call the API from a browser.

## Authentication

Send the access token from `POST /auth/login` in the `Authorization` header:

```
Authorization: Bearer <accessToken>
```

All `GET` endpoints are public, except `/me`, `/me/likes/...` and `/ai-requests/{id}`. Every other endpoint needs a token. A missing, invalid or expired token on a protected endpoint returns `401`, and so does the token of a deleted account: it stops working the moment the account is deleted. A request that sends a token that doesn't work gets `401` even on a public endpoint, so clients should discard such a token.

There are no refresh tokens. When the token expires, the client logs in again.

## Pagination

List endpoints that can grow without limit take `page` (from 0, default 0) and `size` (1–50, default 20) and return:

```json
{
  "content": [],
  "page": { "number": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
}
```

## Errors

Errors use [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) (`Content-Type: application/problem+json`), with an extra `code` field that clients can rely on:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The request is invalid.",
  "instance": "/posts",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

`errors` is only present for `VALIDATION_FAILED`.

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | A field breaks a rule in [Validation](#validation), or a reply's parent belongs to another post |
| 401 | `UNAUTHENTICATED` | Missing, invalid or expired token, or the token of a deleted account |
| 401 | `INVALID_CREDENTIALS` | Wrong email or password. The response is the same for both, so it does not reveal which emails are registered. |
| 403 | `NOT_AUTHOR` | Editing or deleting someone else's post or comment |
| 403 | `INVALID_PASSWORD` | Wrong password when deleting the account |
| 404 | `NOT_FOUND` | The resource does not exist, or is not visible to the caller |
| 409 | `EMAIL_TAKEN`, `USERNAME_TAKEN` | Registration with an email or username that is already used. The usernames of deleted accounts stay taken. |
| 409 | `TARGET_DELETED` | Editing, liking, replying to or asking the AI about a deleted comment |
| 409 | `EDIT_CONFLICT` | Someone saved the same post or comment at the same moment; reload and retry |
| 500 | `INTERNAL_ERROR` | Unexpected error. The response contains no internal details. |

## Validation

| Field | Rule |
|---|---|
| `email` | A valid address, at most 254 characters. Stored in lower case. |
| `username` | 3–30 characters: letters, digits, `_` and `-`. Unique ignoring case. |
| `password` | At least 8 characters and at most 72 bytes in UTF-8 (the bcrypt limit) |
| post `title` | Not blank, at most 200 characters |
| post `content` | Not blank, at most 50,000 characters |
| comment `content` | Not blank, at most 10,000 characters |
| `ids` (like lookups) | 1–100 IDs |

## Resources

**User** (public view):
```json
{ "id": "…", "username": "alice", "bot": false, "createdAt": "2026-11-05T12:00:00Z" }
```

**Me** (the current user): `id`, `email`, `username`, `createdAt`.

**Post:**
```json
{
  "id": "…",
  "title": "Why I chose a modular monolith",
  "content": "…",
  "author": { "id": "…", "username": "alice", "bot": false },
  "tldr": "A short summary of the post.",
  "tldrStatus": "GENERATED",
  "likeCount": 12,
  "createdAt": "2026-11-05T12:00:00Z",
  "updatedAt": "2026-11-05T12:30:00Z"
}
```
- `author` is `null` if the author deleted their account. Clients show "[deleted]".
- `tldr` and `tldrStatus` arrive with the `ai` step, and `likeCount` with the `like` step. Until then, posts are returned without them.
- `tldrStatus` is `PENDING`, `GENERATED` or `FAILED`.
- While the status is `PENDING`, `tldr` may still hold the TL;DR of the previous version, which clients can show as "updating". It is `null` for a new post.

**PostSummary** (list item): the same as Post without `content`. The TL;DR serves as the summary.

**Comment:**
```json
{
  "id": "…",
  "postId": "…",
  "parentId": null,
  "author": { "id": "…", "username": "blog-ai", "bot": true },
  "content": "…",
  "deleted": false,
  "likeCount": 3,
  "createdAt": "2026-11-05T12:05:00Z",
  "updatedAt": "2026-11-05T12:05:00Z"
}
```
For a deleted comment, `deleted` is `true`, and `content` and `author` are `null`. If only the author's account is deleted, the comment keeps its content and only `author` is `null`. Clients show "[deleted]" for a missing author in both cases.

**AiRequest:**
```json
{
  "id": "…",
  "status": "COMPLETED",
  "postId": "…",
  "commentId": null,
  "resultCommentId": "…",
  "createdAt": "2026-11-05T12:06:00Z",
  "completedAt": "2026-11-05T12:06:04Z"
}
```
- `status` is `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED` or `CANCELLED`.
- `postId`, `commentId` and `resultCommentId` become `null` if the post is deleted.
- The reason for a failure is not exposed; clients show a generic message for `FAILED` and `CANCELLED`.

## Endpoints

### Auth

| Method | Path | Access | Success | Errors |
|---|---|---|---|---|
| POST | `/auth/register` | public | `201` Me | 400, 409 |
| POST | `/auth/login` | public | `200` token | 400, 401 |

- Register body: `{ "email", "username", "password" }`. Registering does not log the user in. The email of a deleted account can be used again; its username cannot.
- Login body: `{ "email", "password" }`. Response: `{ "accessToken": "…", "tokenType": "Bearer", "expiresIn": 3600 }`, where `expiresIn` is in seconds.

### Users

| Method | Path | Access | Success | Errors |
|---|---|---|---|---|
| GET | `/me` | token | `200` Me | 401 |
| POST | `/me/delete` | token | `204` | 400, 401, 403 |
| GET | `/users/{id}` | public | `200` User | 404 |

- `POST /me/delete` deletes the current user's account, the way Reddit does it. Body: `{ "password" }`. The account's posts, comments and likes stay, with the author shown as "[deleted]"; to remove content, delete it first. The username is retired for good, and every token of the account stops working at once. This cannot be undone. The full flow is in [DOMAIN.md](DOMAIN.md#flows).
- `GET /users/{id}` returns `404` for a deleted account.

### Posts

| Method | Path | Access | Success | Errors |
|---|---|---|---|---|
| GET | `/posts` | public | `200` page of PostSummary | 400 |
| GET | `/posts/{id}` | public | `200` Post | 404 |
| POST | `/posts` | token | `201` Post, with a `Location` header | 400, 401 |
| PUT | `/posts/{id}` | author | `200` Post | 400, 401, 403, 404, 409 |
| DELETE | `/posts/{id}` | author | `204` | 401, 403, 404 |

- `GET /posts` returns the newest first. The optional `authorId` parameter lists one author's posts; for a deleted account, the page is empty.
- Create and update bodies: `{ "title", "content" }`. A new post starts with `tldrStatus: PENDING`, and so does an update that changes the content.
- Deleting a post also deletes its comments and likes.

### Comments

| Method | Path | Access | Success | Errors |
|---|---|---|---|---|
| GET | `/posts/{id}/comments` | public | `200` array of Comment | 404 |
| POST | `/posts/{id}/comments` | token | `201` Comment, with a `Location` header | 400, 401, 404, 409 |
| GET | `/comments/{id}` | public | `200` Comment | 404 |
| PUT | `/comments/{id}` | author | `200` Comment | 400, 401, 403, 404, 409 |
| DELETE | `/comments/{id}` | author | `204` | 401, 403, 404 |

- `GET /posts/{id}/comments` returns the post's whole thread as a flat list, oldest first. Clients build the tree from `parentId`. A flat list keeps responses shallow however deep a thread gets.
- Create body: `{ "content", "parentId" }`. `parentId` is `null` for a top-level comment. The parent must belong to the same post (`400`) and must not be deleted (`409`).
- Update body: `{ "content" }`.
- `DELETE` is a soft delete. Deleting an already deleted comment returns `204`.

### Likes

| Method | Path | Access | Success | Errors |
|---|---|---|---|---|
| PUT | `/posts/{id}/like` | token | `204` | 401, 404 |
| DELETE | `/posts/{id}/like` | token | `204` | 401, 404 |
| PUT | `/comments/{id}/like` | token | `204` | 401, 404, 409 |
| DELETE | `/comments/{id}/like` | token | `204` | 401, 404 |
| GET | `/me/likes/posts?ids=…` | token | `200` `{ "likedIds": [] }` | 400, 401 |
| GET | `/me/likes/comments?ids=…` | token | `200` `{ "likedIds": [] }` | 400, 401 |

- `PUT` and `DELETE` are idempotent: liking twice, or unliking something not liked, still returns `204` and changes nothing.
- Post and comment responses do not say whether the current user liked them, because the `post` and `comment` modules cannot depend on `like`. Clients pass the IDs they are displaying (comma-separated) to the lookup endpoints, which return the subset the current user has liked.

### AI

| Method | Path | Access | Success | Errors |
|---|---|---|---|---|
| POST | `/posts/{id}/ai-reply` | token | `202` AiRequest, with a `Location` header | 401, 404 |
| POST | `/comments/{id}/ai-reply` | token | `202` AiRequest, with a `Location` header | 401, 404, 409 |
| GET | `/ai-requests/{id}` | the requester | `200` AiRequest | 401, 404 |

- There is no request body: the AI replies to the target in the context of its post and thread.
- The bot's answer is a comment by the bot user: a top-level comment for `/posts/{id}/ai-reply`, a reply to the target for `/comments/{id}/ai-reply`.
- AI use is not limited: a user may ask as often as they like.
- Clients poll `GET /ai-requests/{id}` (every few seconds) until the status is `COMPLETED`, `FAILED` or `CANCELLED`, then load the reply with `GET /comments/{resultCommentId}`.
- `GET /ai-requests/{id}` returns `404` to everyone except the requester. TL;DR jobs are not exposed; their outcome is visible as the post's `tldrStatus`.

## Concurrency

The `version` check on posts and comments only protects saves that happen at the same moment on the server (`409 EDIT_CONFLICT`). Between clients, edits are last-write-wins: the API does not use `ETag`/`If-Match` yet.
