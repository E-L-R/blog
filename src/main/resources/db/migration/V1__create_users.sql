CREATE TABLE users
(
    id            uuid PRIMARY KEY,
    email         varchar(254),
    username      varchar(30) NOT NULL,
    password_hash varchar(100),
    role          varchar(10) NOT NULL,
    deleted_at    timestamptz,
    created_at    timestamptz NOT NULL,

    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_email_lower_case CHECK (email = lower(email)),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'BOT')),
    CONSTRAINT users_email_presence CHECK ((email IS NULL) = (role = 'BOT' OR deleted_at IS NOT NULL)),
    CONSTRAINT users_password_presence CHECK ((password_hash IS NULL) = (role = 'BOT' OR deleted_at IS NOT NULL)),
    CONSTRAINT users_bot_not_deleted CHECK (role <> 'BOT' OR deleted_at IS NULL)
);

CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));

INSERT INTO users (id, username, role, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'blog-ai', 'BOT', now());