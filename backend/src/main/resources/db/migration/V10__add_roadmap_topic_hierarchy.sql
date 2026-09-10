-- 기존 학습 단계는 대주제 단독 단계로 유지하고, 새 로드맵은 같은 테이블의 원자 단계를
-- 대주제/소주제 메타데이터로 묶는다. 퀴즈의 roadmap_step_id 연결은 그대로 재사용한다.
ALTER TABLE learning_roadmap_step
    ADD COLUMN major_topic_key VARCHAR(64),
    ADD COLUMN major_topic_title VARCHAR(255),
    ADD COLUMN major_topic_description TEXT,
    ADD COLUMN major_topic_topic VARCHAR(255),
    ADD COLUMN subtopic_key VARCHAR(48),
    ADD COLUMN subtopic_title VARCHAR(255);

UPDATE learning_roadmap_step
SET major_topic_key = step_key,
    major_topic_title = title,
    major_topic_description = description,
    major_topic_topic = topic;

ALTER TABLE learning_roadmap_step
    ALTER COLUMN major_topic_key SET NOT NULL,
    ALTER COLUMN major_topic_title SET NOT NULL,
    ALTER COLUMN major_topic_topic SET NOT NULL;

CREATE INDEX idx_learning_roadmap_step_major_topic
    ON learning_roadmap_step (roadmap_id, major_topic_key, step_order);
