CREATE TABLE daily_learning (
    id BIGSERIAL PRIMARY KEY,
    learning_date DATE NOT NULL,
    article_title VARCHAR(255) NOT NULL,
    article_url VARCHAR(2048) NOT NULL,
    article_published_at TIMESTAMP NULL,
    article_summary TEXT NOT NULL,
    supplement TEXT NOT NULL,
    concepts TEXT NOT NULL DEFAULT '[]',
    review_topic VARCHAR(255) NOT NULL,
    recommended_review_question_count INTEGER NOT NULL,
    source_document_id BIGINT NULL REFERENCES source_document(id),
    status VARCHAR(32) NOT NULL,
    completed_at TIMESTAMP NULL,
    generation_error VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_daily_learning_date UNIQUE (learning_date),
    CONSTRAINT uq_daily_learning_article_url UNIQUE (article_url),
    CONSTRAINT ck_daily_learning_question_count CHECK (recommended_review_question_count BETWEEN 1 AND 20)
);

CREATE INDEX idx_daily_learning_status_date ON daily_learning(status, learning_date DESC);

ALTER TABLE learning_quiz
    ADD COLUMN daily_learning_id BIGINT NULL REFERENCES daily_learning(id),
    ADD COLUMN daily_learning_track VARCHAR(32) NULL;

CREATE INDEX idx_learning_quiz_daily_learning ON learning_quiz(daily_learning_id, daily_learning_track);
