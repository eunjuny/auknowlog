ALTER TABLE learning_quiz
    ADD COLUMN roadmap_step_id BIGINT REFERENCES learning_roadmap_step(id) ON DELETE SET NULL;

CREATE INDEX idx_learning_quiz_roadmap_step
    ON learning_quiz (roadmap_step_id);
