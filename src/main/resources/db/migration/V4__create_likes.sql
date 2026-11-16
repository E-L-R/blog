-- Like counts are caches of the like rows below. Only SQL statements update them,
-- in the same transaction as the like row (see docs/DATABASE.md, "Columns written by SQL").
ALTER TABLE posts
    ADD COLUMN like_count integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT posts_like_count_not_negative CHECK (like_count >= 0);

ALTER TABLE comments
    ADD COLUMN like_count integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT comments_like_count_not_negative CHECK (like_count >= 0);

CREATE TABLE post_likes
(
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL,
    post_id    uuid        NOT NULL,
    created_at timestamptz NOT NULL,

    -- One like per user and post. Its index also answers "which of these posts has this user liked?".
    CONSTRAINT post_likes_user_id_post_id_key UNIQUE (user_id, post_id),
    CONSTRAINT post_likes_user_fkey FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT post_likes_post_fkey FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
);

CREATE INDEX post_likes_post_id_idx ON post_likes (post_id);

CREATE TABLE comment_likes
(
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL,
    comment_id uuid        NOT NULL,
    created_at timestamptz NOT NULL,

    CONSTRAINT comment_likes_user_id_comment_id_key UNIQUE (user_id, comment_id),
    CONSTRAINT comment_likes_user_fkey FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT comment_likes_comment_fkey FOREIGN KEY (comment_id) REFERENCES comments (id) ON DELETE CASCADE
);

CREATE INDEX comment_likes_comment_id_idx ON comment_likes (comment_id);
