/**
 * The API's resources, as docs/API.md#resources describes them. Fields that arrive with a
 * later backend step (the TL;DR with the `ai` step) are optional, so the app works both
 * before and after that step.
 */

/** An ID: a UUID string. */
export type Id = string;

/** A time: an ISO 8601 string in UTC, such as `2026-11-05T12:00:00Z`. */
export type Timestamp = string;

/** The current user's own account (`GET /me`, `POST /auth/register`). */
export interface Me {
  id: Id;
  email: string;
  username: string;
  createdAt: Timestamp;
}

/** A user's public profile (`GET /users/{id}`). */
export interface User {
  id: Id;
  username: string;
  bot: boolean;
  createdAt: Timestamp;
}

/**
 * The author embedded in a post or comment. A post's `author` is `null` once its author
 * deletes their account; the app shows "[deleted]" instead.
 */
export interface Author {
  id: Id;
  username: string;
  bot: boolean;
}

export type TldrStatus = 'PENDING' | 'GENERATED' | 'FAILED';

/** A post as `GET /posts/{id}` returns it. */
export interface Post {
  id: Id;
  title: string;
  content: string;
  author: Author | null;
  /** While `tldrStatus` is `PENDING`, this may still be the previous version's TL;DR. */
  tldr?: string | null;
  tldrStatus?: TldrStatus;
  /** Optional: posts had no like count before the backend's `like` step. */
  likeCount?: number;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** A post in a list (`GET /posts`): the same as {@link Post}, without the content. */
export type PostSummary = Omit<Post, 'content'>;

/**
 * A comment (`GET /posts/{id}/comments`). A thread arrives as a flat list, oldest first; the
 * app builds the tree from `parentId`. A deleted comment keeps its place in the thread, with
 * `content` and `author` set to `null`. When only its author's account is deleted, the comment
 * keeps its content and `author` is `null`.
 */
export interface Comment {
  id: Id;
  postId: Id;
  /** `null` for a top-level comment. */
  parentId: Id | null;
  author: Author | null;
  content: string | null;
  deleted: boolean;
  likeCount: number;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** The body of `POST /posts/{id}/comments`. */
export interface CommentInput {
  content: string;
  parentId: Id | null;
}

export type AiRequestStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

/** A request for an AI reply (`POST /posts/{id}/ai-reply`, `GET /ai-requests/{id}`). */
export interface AiRequest {
  id: Id;
  status: AiRequestStatus;
  postId: Id | null;
  /** The comment the AI replies to; `null` for a reply to the post. */
  commentId: Id | null;
  /** The bot's comment, once the request is `COMPLETED`. */
  resultCommentId: Id | null;
  createdAt: Timestamp;
  completedAt: Timestamp | null;
}

/** The answer of `GET /me/likes/posts` and `/me/likes/comments`. */
export interface LikedIds {
  likedIds: Id[];
}

/** One page of a list endpoint (docs/API.md#pagination). `number` counts from 0. */
export interface Page<T> {
  content: T[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
}

/** The response of `POST /auth/login`. `expiresIn` is in seconds. */
export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

/** The body of a create or update request for a post. */
export interface PostInput {
  title: string;
  content: string;
}

/** One broken rule in a `VALIDATION_FAILED` error. */
export interface FieldError {
  field: string;
  message: string;
}

/** An error response: RFC 9457 Problem Details with the API's `code` (docs/API.md#errors). */
export interface ProblemDetail {
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code: string;
  errors?: FieldError[];
}
