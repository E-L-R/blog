/**
 * The mock API's data: an in-memory copy of the backend's rules for users and posts, as
 * docs/DOMAIN.md describes them. The handlers in handlers.ts call it.
 *
 * It follows the design, including parts the backend hasn't built yet (login, the TL;DR), so
 * the app can be built and tested ahead of the backend. Tests use a fresh one each time;
 * mock mode in the browser keeps it in `localStorage` (browser.ts).
 */
import type { Author, Me, Post, PostSummary, TldrStatus, User } from '@/api/types';

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

export interface MockState {
  users: MockUser[];
  posts: MockPost[];
  seq: number;
}

export interface MockDbOptions {
  /** The current time in milliseconds. Tests pass their own. */
  now?: () => number;
  /** How long the fake AI takes to write a TL;DR. */
  tldrDelayMs?: number;
}

export class MockDb {
  state: MockState;
  readonly now: () => number;
  tldrDelayMs: number;

  constructor(options: MockDbOptions = {}, state?: MockState) {
    this.now = options.now ?? Date.now;
    this.tldrDelayMs = options.tldrDelayMs ?? 2500;
    this.state = state ?? MockDb.emptyState(this.now());
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
      seq: 0,
    };
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

  deletePost(post: MockPost): void {
    this.state.posts = this.state.posts.filter((candidate) => candidate.id !== post.id);
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
      createdAt: post.createdAt,
      updatedAt: post.updatedAt,
    };
  }

  postSummaryView(post: MockPost): PostSummary {
    const { content: _content, ...summary } = this.postView(post);
    void _content;
    return summary;
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
