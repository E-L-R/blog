-- TL;DR columns on posts. content_hash identifies the content a TL;DR was generated from
-- (see docs/DECISIONS.md, D5). Existing posts get their hash computed here, before the
-- column becomes NOT NULL.
ALTER TABLE posts
    ADD COLUMN content_hash      char(64),
    ADD COLUMN tldr              text,
    ADD COLUMN tldr_source_hash  char(64),
    ADD COLUMN tldr_generated_at timestamptz,
    ADD COLUMN tldr_failed_hash  char(64);

UPDATE posts SET content_hash = encode(sha256(convert_to(content, 'UTF8')), 'hex');

ALTER TABLE posts ALTER COLUMN content_hash SET NOT NULL;

-- Every LLM call is a row: the table is the job queue and the audit trail.
-- The application never deletes rows; deleting a post only detaches its requests.
CREATE TABLE ai_requests
(
    id                uuid PRIMARY KEY,
    type              varchar(10)  NOT NULL,
    status            varchar(12)  NOT NULL,
    requested_by      uuid,
    post_id           uuid,
    comment_id        uuid,
    result_comment_id uuid,
    model             varchar(100),
    attempts          integer      NOT NULL DEFAULT 0,
    next_attempt_at   timestamptz  NOT NULL,
    locked_until      timestamptz,
    error             text,
    created_at        timestamptz  NOT NULL,
    completed_at      timestamptz,

    CONSTRAINT ai_requests_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ai_requests_post_fkey FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE SET NULL,
    CONSTRAINT ai_requests_comment_fkey FOREIGN KEY (comment_id) REFERENCES comments (id) ON DELETE SET NULL,
    CONSTRAINT ai_requests_result_comment_fkey FOREIGN KEY (result_comment_id) REFERENCES comments (id) ON DELETE SET NULL,
    CONSTRAINT ai_requests_type_check CHECK (type IN ('TLDR', 'REPLY')),
    CONSTRAINT ai_requests_status_check CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ai_requests_attempts_not_negative CHECK (attempts >= 0),
    -- A TL;DR is requested by the system, about a post only
    CONSTRAINT ai_requests_tldr_shape CHECK (type <> 'TLDR' OR (requested_by IS NULL AND comment_id IS NULL AND result_comment_id IS NULL)),
    -- A reply is always requested by a user
    CONSTRAINT ai_requests_reply_requested_by CHECK (type <> 'REPLY' OR requested_by IS NOT NULL),
    -- Only a running job has a lease
    CONSTRAINT ai_requests_lease_presence CHECK ((status = 'IN_PROGRESS') = (locked_until IS NOT NULL)),
    -- Only a finished job has a completion time
    CONSTRAINT ai_requests_completed_at_presence CHECK ((status IN ('COMPLETED', 'FAILED', 'CANCELLED')) = (completed_at IS NOT NULL))
);

-- Finding due jobs and expired leases; partial indexes stay small because finished jobs are left out
CREATE INDEX ai_requests_due_idx ON ai_requests (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ai_requests_lease_idx ON ai_requests (locked_until) WHERE status = 'IN_PROGRESS';
CREATE INDEX ai_requests_requested_by_idx ON ai_requests (requested_by);
CREATE INDEX ai_requests_post_id_created_at_idx ON ai_requests (post_id, created_at);
CREATE INDEX ai_requests_comment_id_idx ON ai_requests (comment_id);
CREATE INDEX ai_requests_result_comment_id_idx ON ai_requests (result_comment_id);
