/**
 * The mock API's data: an in-memory copy of the backend's rules for users and posts, as
 * docs/DOMAIN.md describes them. The handlers in handlers.ts call it.
 *
 * It follows the design, not the backend's code, so a page can be built and tested before its
 * endpoint exists, and without Docker. Tests use a fresh one each time;
 * mock mode in the browser keeps it in `localStorage` (browser.ts).
 */
import type {
  AiRequest,
  AiRequestStatus,
  Author,
  Comment,
  Me,
  Post,
  PostSummary,
  TldrStatus,
  User,
} from '@/api/types';

export const BOT_USERNAME = 'blog-ai';

export interface MockUser {
  id: string;
  /** `null` once the account is deleted. */
  email: string | null;
  username: string;
  /** Stored as given: this is a mock. `null` for the bot and deleted accounts. */
  password: string | null;
  bot: boolean;
  deleted: boolean;
  createdAt: string;
}

export interface MockPost {
  id: string;
  /** Orders posts created in the same millisecond. */
  seq: number;
  authorId: string;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  tldr: string | null;
  tldrStatus: TldrStatus;
  /** When the fake AI finishes the pending TL;DR (milliseconds since the epoch). */
  tldrReadyAt: number | null;
}

export interface MockComment {
  id: string;
  seq: number;
  postId: string;
  parentId: string | null;
  authorId: string;
  /** `null` once deleted. */
  content: string | null;
  deleted: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MockAiRequest {
  id: string;
  requestedById: string;
  postId: string | null;
  commentId: string | null;
  status: AiRequestStatus;
  resultCommentId: string | null;
  createdAt: string;
  completedAt: string | null;
  /** When the fake AI answers (milliseconds since the epoch). */
  readyAt: number;
}

export interface MockState {
  users: MockUser[];
  posts: MockPost[];
  comments: MockComment[];
  /** One `"<userId>:<postId>"` per like: the database's unique constraint, as a set. */
  postLikes: string[];
  commentLikes: string[];
  aiRequests: MockAiRequest[];
  seq: number;
}

export interface MockDbOptions {
  /** The current time in milliseconds. Tests pass their own. */
  now?: () => number;
  /** How long the fake AI takes to write a TL;DR. */
  tldrDelayMs?: number;
  /** How long the fake AI takes to write a reply. */
  replyDelayMs?: number;
}

export class MockDb {
  state: MockState;
  readonly now: () => number;
  tldrDelayMs: number;
  replyDelayMs: number;

  constructor(options: MockDbOptions = {}, state?: MockState) {
    this.now = options.now ?? Date.now;
    this.tldrDelayMs = options.tldrDelayMs ?? 2500;
    this.replyDelayMs = options.replyDelayMs ?? 3000;
    // Data saved by an older version of the mock has no comments, likes or AI requests yet.
    this.state = state
      ? {
          ...state,
          comments: state.comments ?? [],
          postLikes: state.postLikes ?? [],
          commentLikes: state.commentLikes ?? [],
          aiRequests: state.aiRequests ?? [],
        }
      : MockDb.emptyState(this.now());
  }

  /** No users but the bot, and no posts: the state after the backend's migrations. */
  static emptyState(now: number): MockState {
    return {
      users: [
        {
          id: crypto.randomUUID(),
          email: null,
          username: BOT_USERNAME,
          password: null,
          bot: true,
          deleted: false,
          createdAt: new Date(now).toISOString(),
        },
      ],
      posts: [],
      comments: [],
      postLikes: [],
      commentLikes: [],
      aiRequests: [],
      seq: 0,
    };
  }

  get bot(): MockUser {
    return this.state.users.find((user) => user.bot)!;
  }

  private timestamp(): string {
    return new Date(this.now()).toISOString();
  }

  // ---- users ----

  findUser(id: string): MockUser | undefined {
    return this.state.users.find((user) => user.id === id);
  }

  /** Taken ignoring case, including the usernames of deleted accounts and the bot's. */
  usernameTaken(username: string): boolean {
    const lower = username.toLowerCase();
    return this.state.users.some((user) => user.username.toLowerCase() === lower);
  }

  /** Only active accounts hold an email; a deleted account's email is free again. */
  emailTaken(email: string): boolean {
    const lower = email.toLowerCase();
    return this.state.users.some((user) => user.email === lower);
  }

  createUser(email: string, username: string, password: string): MockUser {
    const user: MockUser = {
      id: crypto.randomUUID(),
      email: email.toLowerCase(),
      username,
      password,
      bot: false,
      deleted: false,
      createdAt: this.timestamp(),
    };
    this.state.users.push(user);
    return user;
  }

  /** The active account with this email and password, if any. */
  authenticate(email: string, password: string): MockUser | undefined {
    const lower = email.toLowerCase();
    return this.state.users.find(
      (user) => !user.deleted && user.email === lower && user.password === password,
    );
  }

  /** Deletes an account the way docs/DOMAIN.md#flows describes: a tombstone stays. */
  deleteUser(user: MockUser): void {
    user.deleted = true;
    user.email = null;
    user.password = null;
  }

  // ---- posts ----

  findPost(id: string): MockPost | undefined {
    const post = this.state.posts.find((candidate) => candidate.id === id);
    if (post) this.finishTldr(post);
    return post;
  }

  /** Newest first, optionally one author's. A deleted author's list is empty. */
  listPosts(authorId?: string): MockPost[] {
    let posts = this.state.posts;
    if (authorId !== undefined) {
      const author = this.findUser(authorId);
      if (!author || author.deleted) return [];
      posts = posts.filter((post) => post.authorId === authorId);
    }
    posts.forEach((post) => this.finishTldr(post));
    return [...posts].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.seq - a.seq);
  }

  createPost(authorId: string, title: string, content: string): MockPost {
    const now = this.timestamp();
    const post: MockPost = {
      id: crypto.randomUUID(),
      seq: ++this.state.seq,
      authorId,
      title,
      content,
      createdAt: now,
      updatedAt: now,
      tldr: null,
      tldrStatus: 'PENDING',
      tldrReadyAt: this.now() + this.tldrDelayMs,
    };
    this.state.posts.push(post);
    return post;
  }

  /** A content change queues a new TL;DR; a title-only edit doesn't. */
  editPost(post: MockPost, title: string, content: string): void {
    if (post.content !== content) {
      post.tldrStatus = 'PENDING';
      post.tldrReadyAt = this.now() + this.tldrDelayMs;
    }
    post.title = title;
    post.content = content;
    post.updatedAt = this.timestamp();
  }

  /**
   * Deletes the post with its comments and likes, as the database's cascades do. AI requests
   * stay, with their references set to `null` (docs/DECISIONS.md, D8).
   */
  deletePost(post: MockPost): void {
    const commentIds = new Set(
      this.state.comments.filter((c) => c.postId === post.id).map((c) => c.id),
    );
    this.state.posts = this.state.posts.filter((candidate) => candidate.id !== post.id);
    this.state.comments = this.state.comments.filter((c) => c.postId !== post.id);
    this.state.postLikes = this.state.postLikes.filter((like) => !like.endsWith(`:${post.id}`));
    this.state.commentLikes = this.state.commentLikes.filter(
      (like) => !commentIds.has(like.split(':')[1]!),
    );
    for (const request of this.state.aiRequests) {
      if (request.postId === post.id) {
        request.postId = null;
        request.commentId = null;
        request.resultCommentId = null;
      }
    }
  }

  // ---- comments ----

  findComment(id: string): MockComment | undefined {
    return this.state.comments.find((comment) => comment.id === id);
  }

  /** The post's whole thread, oldest first. */
  thread(postId: string): MockComment[] {
    this.finishReplies();
    return this.state.comments
      .filter((comment) => comment.postId === postId)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.seq - b.seq);
  }

  createComment(
    postId: string,
    authorId: string,
    content: string,
    parentId: string | null,
  ): MockComment {
    const now = this.timestamp();
    const comment: MockComment = {
      id: crypto.randomUUID(),
      seq: ++this.state.seq,
      postId,
      parentId,
      authorId,
      content,
      deleted: false,
      createdAt: now,
      updatedAt: now,
    };
    this.state.comments.push(comment);
    return comment;
  }

  editComment(comment: MockComment, content: string): void {
    comment.content = content;
    comment.updatedAt = this.timestamp();
  }

  /** A soft delete: the comment keeps its place, its replies and its likes. */
  deleteComment(comment: MockComment): void {
    if (comment.deleted) return;
    comment.deleted = true;
    comment.content = null;
    comment.updatedAt = this.timestamp();
  }

  // ---- likes ----

  /** Likes or unlikes. Liking twice, or unliking what isn't liked, changes nothing. */
  like(kind: 'post' | 'comment', userId: string, targetId: string, liked: boolean): void {
    const list = kind === 'post' ? this.state.postLikes : this.state.commentLikes;
    const key = `${userId}:${targetId}`;
    const index = list.indexOf(key);
    if (liked && index === -1) list.push(key);
    if (!liked && index !== -1) list.splice(index, 1);
  }

  likeCount(kind: 'post' | 'comment', targetId: string): number {
    const list = kind === 'post' ? this.state.postLikes : this.state.commentLikes;
    return list.filter((key) => key.endsWith(`:${targetId}`)).length;
  }

  likedIds(kind: 'post' | 'comment', userId: string, ids: string[]): string[] {
    const list = new Set(kind === 'post' ? this.state.postLikes : this.state.commentLikes);
    return [...new Set(ids)].filter((id) => list.has(`${userId}:${id}`));
  }

  // ---- AI replies ----

  requestReply(userId: string, postId: string, commentId: string | null): MockAiRequest {
    const request: MockAiRequest = {
      id: crypto.randomUUID(),
      requestedById: userId,
      postId,
      commentId,
      status: 'PENDING',
      resultCommentId: null,
      createdAt: this.timestamp(),
      completedAt: null,
      readyAt: this.now() + this.replyDelayMs,
    };
    this.state.aiRequests.push(request);
    return request;
  }

  findAiRequest(id: string): MockAiRequest | undefined {
    this.finishReplies();
    return this.state.aiRequests.find((request) => request.id === id);
  }

  /**
   * The fake AI: once a request's delay has passed, the bot replies. A reply to a comment that
   * was deleted in the meantime, or to a deleted post, is cancelled instead.
   */
  private finishReplies(): void {
    for (const request of this.state.aiRequests) {
      if (request.status !== 'PENDING' || this.now() < request.readyAt) continue;
      request.completedAt = this.timestamp();
      const post = request.postId ? this.state.posts.find((p) => p.id === request.postId) : null;
      const target = request.commentId ? this.findComment(request.commentId) : null;
      if (!post || (request.commentId && (!target || target.deleted))) {
        request.status = 'CANCELLED';
        continue;
      }
      const about = target?.content ?? post.content;
      const reply = this.createComment(
        post.id,
        this.bot.id,
        `Thanks for asking. In short: ${summarize(about)}`,
        request.commentId,
      );
      request.status = 'COMPLETED';
      request.resultCommentId = reply.id;
    }
  }

  /** The fake AI: once the delay has passed, the TL;DR is the content's first sentence. */
  private finishTldr(post: MockPost): void {
    if (post.tldrStatus !== 'PENDING' || post.tldrReadyAt === null) return;
    if (this.now() < post.tldrReadyAt) return;
    post.tldr = summarize(post.content);
    post.tldrStatus = 'GENERATED';
    post.tldrReadyAt = null;
  }

  // ---- views: the JSON the API returns ----

  author(authorId: string): Author | null {
    const user = this.findUser(authorId);
    if (!user || user.deleted) return null;
    return { id: user.id, username: user.username, bot: user.bot };
  }

  postView(post: MockPost): Post {
    return {
      id: post.id,
      title: post.title,
      content: post.content,
      author: this.author(post.authorId),
      tldr: post.tldr,
      tldrStatus: post.tldrStatus,
      likeCount: this.likeCount('post', post.id),
      createdAt: post.createdAt,
      updatedAt: post.updatedAt,
    };
  }

  postSummaryView(post: MockPost): PostSummary {
    const { content: _content, ...summary } = this.postView(post);
    void _content;
    return summary;
  }

  commentView(comment: MockComment): Comment {
    return {
      id: comment.id,
      postId: comment.postId,
      parentId: comment.parentId,
      author: comment.deleted ? null : this.author(comment.authorId),
      content: comment.deleted ? null : comment.content,
      deleted: comment.deleted,
      likeCount: this.likeCount('comment', comment.id),
      createdAt: comment.createdAt,
      updatedAt: comment.updatedAt,
    };
  }

  static aiRequestView(request: MockAiRequest): AiRequest {
    return {
      id: request.id,
      status: request.status,
      postId: request.postId,
      commentId: request.commentId,
      resultCommentId: request.resultCommentId,
      createdAt: request.createdAt,
      completedAt: request.completedAt,
    };
  }

  static meView(user: MockUser): Me {
    return {
      id: user.id,
      email: user.email ?? '',
      username: user.username,
      createdAt: user.createdAt,
    };
  }

  static userView(user: MockUser): User {
    return { id: user.id, username: user.username, bot: user.bot, createdAt: user.createdAt };
  }
}

function summarize(content: string): string {
  const text = content.replace(/\s+/g, ' ').trim();
  const sentence = /^.+?[.!?](\s|$)/.exec(text)?.[0].trim() ?? text;
  return sentence.length <= 180 ? sentence : `${sentence.slice(0, 177).trimEnd()}…`;
}
