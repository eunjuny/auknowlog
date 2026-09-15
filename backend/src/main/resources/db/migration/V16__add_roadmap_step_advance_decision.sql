-- 문제 수를 채운 시점과 다음 단계로 넘어가기로 한 시점을 분리한다.
-- 기존에 완료된 이력은 과거 동작을 보존하도록 이미 승인된 것으로 보정한다.
ALTER TABLE learning_roadmap_step
    ADD COLUMN advance_confirmed_at TIMESTAMP;

UPDATE learning_roadmap_step step
SET advance_confirmed_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM learning_quiz quiz
    JOIN learning_attempt attempt ON attempt.quiz_id = quiz.id
    WHERE quiz.roadmap_step_id = step.id
    GROUP BY quiz.roadmap_step_id
    HAVING SUM(attempt.total_questions) >= step.question_target
);
