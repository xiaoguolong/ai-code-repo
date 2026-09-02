CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(64)  NOT NULL UNIQUE,
    model       VARCHAR(128) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(64)  NOT NULL,
    role        VARCHAR(32)  NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(session_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_id ON chat_message(session_id);

CREATE TABLE IF NOT EXISTS token_record (
    id                BIGSERIAL PRIMARY KEY,
    session_id        VARCHAR(64)  NOT NULL,
    model             VARCHAR(128) NOT NULL,
    prompt_tokens     INT          NOT NULL,
    completion_tokens INT          NOT NULL,
    total_tokens      INT          NOT NULL,
    latency_ms        BIGINT       NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    error_code        VARCHAR(64),
    occurred_at       TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_token_record_session FOREIGN KEY (session_id) REFERENCES chat_session(session_id)
);

CREATE INDEX IF NOT EXISTS idx_token_record_session_id ON token_record(session_id);
