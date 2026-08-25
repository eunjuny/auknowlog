CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE question_embedding (
    question_history_id BIGINT PRIMARY KEY REFERENCES question_history(id) ON DELETE CASCADE,
    embedding vector(512) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_question_embedding_cosine
    ON question_embedding USING hnsw (embedding vector_cosine_ops);
