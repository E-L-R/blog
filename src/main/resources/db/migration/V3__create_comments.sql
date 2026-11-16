CREATE TABLE comments
(
    id         uuid PRIMARY KEY,
    post_id    uuid        NOT NULL,
    parent_id  uuid,
    author_id  uuid        NOT NULL,
    content    text,
    deleted    boolean     NOT NULL DEFAULT false,
    version    bigint      NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,

    -- Lets the parent foreign key below include post_id
    CONSTRAINT comments_id_post_id_key UNIQUE (id, post_id),
    CONSTRAINT comments_post_fkey FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    -- A reply must be on the same post as its parent. Not checked for top-level comments,
    -- whose parent_id is null.
    CONSTRAINT comments_parent_fkey FOREIGN KEY (parent_id, post_id) REFERENCES comments (id, post_id) ON DELETE CASCADE,
    CONSTRAINT comments_author_fkey FOREIGN KEY (author_id) REFERENCES users (id),
    -- A comment has content if and only if it is not deleted
    CONSTRAINT comments_content_presence CHECK (deleted = (content IS NULL))
);

CREATE INDEX comments_post_id_created_at_idx ON comments (post_id, created_at);
CREATE INDEX comments_parent_id_idx ON comments (parent_id);
CREATE INDEX comments_author_id_idx ON comments (author_id);
