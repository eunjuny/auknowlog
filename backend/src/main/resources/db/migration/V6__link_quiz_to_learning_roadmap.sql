ALTER TABLE learning_quiz
    ADD COLUMN roadmap_id BIGINT REFERENCES learning_roadmap(id) ON DELETE SET NULL;

CREATE INDEX idx_learning_quiz_roadmap
    ON learning_quiz (roadmap_id);
