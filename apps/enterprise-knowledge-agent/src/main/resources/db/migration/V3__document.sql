CREATE TABLE IF NOT EXISTS document (
    id                BIGSERIAL PRIMARY KEY,
    document_id       VARCHAR(64)  NOT NULL UNIQUE,
    knowledge_base_id BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    chunk_count       INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_knowledge_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id)
);

CREATE INDEX IF NOT EXISTS idx_document_knowledge_base_id ON document(knowledge_base_id);
