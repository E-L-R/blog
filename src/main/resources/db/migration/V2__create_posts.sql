CREATE TABLE posts
(
    id         uuid PRIMARY KEY,
    author_id  uuid         NOT NULL,
    title      varchar(200) NOT NULL,
    content    text         NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,

    CONSTRAINT posts_author_fkey FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX posts_created_at_idx ON posts (created_at);
CREATE INDEX posts_author_id_created_at_idx ON posts (author_id, created_at);
