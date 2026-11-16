/**
 * Demo data for mock mode in the browser, so the app doesn't start empty: users, posts, a
 * comment thread with an AI reply and a deleted comment, and likes. Tests don't use it: each
 * test creates exactly the data it needs.
 */
import { MockDb } from './db';

export const DEMO_EMAIL = 'demo@example.com';
export const DEMO_PASSWORD = 'password123';

const HOUR = 3_600_000;

export function seed(db: MockDb): void {
  const now = db.now();
  const demo = db.createUser(DEMO_EMAIL, 'demo', DEMO_PASSWORD);
  const ada = db.createUser('ada@example.com', 'ada', 'password123');
  const grace = db.createUser('grace@example.com', 'grace_h', 'password123');
  const gone = db.createUser('gone@example.com', 'former-member', 'password123');

  const posts: [string, string, string, number][] = [
    [
      ada.id,
      'Why I chose a modular monolith',
      'Microservices solve problems most teams do not have yet. A modular monolith keeps one deployable app, but splits the code into modules whose boundaries a test checks on every build.\n\nThe modules talk through small public services and refer to each other by ID. If one of them ever needs to become a service of its own, the seams are already there.',
      30 * HOUR,
    ],
    [
      grace.id,
      'A database table is a fine job queue',
      'Before reaching for a message broker, look at what PostgreSQL already gives you. SELECT ... FOR UPDATE SKIP LOCKED lets several workers claim jobs without stepping on each other.\n\nAdd a lease, so a crashed worker’s job is picked up again, and the table doubles as an audit trail.',
      20 * HOUR,
    ],
    [
      gone.id,
      'Notes from my first week of Java 25',
      'Records, pattern matching and virtual threads make everyday Java feel much lighter than it used to. Here are a few things that surprised me.',
      8 * HOUR,
    ],
    [
      demo.id,
      'Hello from the demo account',
      'This post belongs to the demo account, so you can try editing and deleting it.\n\nLog in as demo@example.com with the password password123.',
      2 * HOUR,
    ],
  ];

  const created = posts.map(([authorId, title, content, age]) => {
    const post = db.createPost(authorId, title, content);
    post.createdAt = post.updatedAt = new Date(now - age).toISOString();
    post.tldrReadyAt = now;
    return post;
  });
  const monolith = created[0]!;
  const queue = created[1]!;

  // A thread with replies, a reply by the AI bot, and a deleted comment that keeps its reply.
  const at = (age: number) => new Date(now - age).toISOString();
  const comment = (
    postId: string,
    authorId: string,
    content: string,
    parentId: string | null,
    age: number,
  ) => {
    const c = db.createComment(postId, authorId, content, parentId);
    c.createdAt = c.updatedAt = at(age);
    return c;
  };
  const question = comment(
    monolith.id,
    grace.id,
    'How do you stop the modules from slowly leaking into each other?',
    null,
    28 * HOUR,
  );
  comment(
    monolith.id,
    ada.id,
    'A test checks the boundaries on every build, so a forbidden import fails CI.',
    question.id,
    27 * HOUR,
  );
  comment(
    monolith.id,
    db.bot.id,
    'In short: a build-time check of the allowed dependencies, plus modules that only talk through small public services.',
    question.id,
    26 * HOUR,
  );
  const removed = comment(monolith.id, demo.id, 'First!', null, 25 * HOUR);
  comment(monolith.id, grace.id, 'Please keep it on topic.', removed.id, 24 * HOUR);
  db.deleteComment(removed);
  comment(queue.id, ada.id, 'SKIP LOCKED is underrated.', null, 18 * HOUR);

  db.like('post', ada.id, monolith.id, true);
  db.like('post', grace.id, monolith.id, true);
  db.like('post', demo.id, queue.id, true);
  db.like('comment', ada.id, question.id, true);

  // A deleted account: its post stays, by "[deleted]".
  db.deleteUser(gone);
}
