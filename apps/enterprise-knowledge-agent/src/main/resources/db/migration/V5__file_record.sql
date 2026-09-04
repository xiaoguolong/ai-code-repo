CREATE TABLE IF NOT EXISTS file_record (
    id            BIGSERIAL PRIMARY KEY,
    file_id       VARCHAR(64)  NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(128),
    size_bytes    BIGINT       NOT NULL,
    storage_type  VARCHAR(32)  NOT NULL,
    storage_key   VARCHAR(512) NOT NULL,
    user_id       BIGINT       NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_record_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_file_record_user_id ON file_record(user_id);
