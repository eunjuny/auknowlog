-- 개발용 샘플입니다. 실제 비밀번호나 Keycloak token을 저장하지 않습니다.
BEGIN;
SELECT pg_advisory_xact_lock(9230929);
INSERT INTO app_user (keycloak_subject, username, display_name)
VALUES ('local-sample-test1', 'test1', 'Sample learner 1'),
       ('local-sample-test2', 'test2', 'Sample learner 2')
ON CONFLICT (username) DO NOTHING;

DO $$
DECLARE
    learner RECORD;
    roadmap_id_value BIGINT;
    step_id_value BIGINT;
    quiz_id_value BIGINT;
    question_id_value BIGINT;
    attempt_id_value BIGINT;
    sample_topic TEXT;
    question_text_value TEXT;
    answer_value TEXT;
    wrong_value TEXT;
BEGIN
    FOR learner IN SELECT id, username FROM app_user WHERE username IN ('test1', 'test2') LOOP
        sample_topic := CASE learner.username WHEN 'test1' THEN 'Kubernetes Pod' ELSE 'PostgreSQL 트랜잭션' END;
        IF EXISTS (SELECT 1 FROM learning_quiz WHERE owner_id = learner.id AND title = '[OSC SAMPLE] ' || sample_topic) THEN
            CONTINUE;
        END IF;
        INSERT INTO learning_roadmap(owner_id, title, topic, start_date, end_date, duration_weeks,
            questions_per_week, status, source_type, description)
        VALUES (learner.id, '[OSC SAMPLE] ' || sample_topic || ' 학습', sample_topic,
            CURRENT_DATE, CURRENT_DATE + 13, 2, 2, 'ACTIVE', 'MANUAL', '비용 없이 등록한 사용자 격리 확인용 샘플')
        RETURNING id INTO roadmap_id_value;
        INSERT INTO learning_roadmap_step(roadmap_id, step_key, title, topic, question_target, step_order,
            major_topic_key, major_topic_title, major_topic_topic, subtopic_key, subtopic_title)
        VALUES (roadmap_id_value, 'basics', sample_topic, sample_topic, 3, 1,
            'fundamentals', '기초 개념', sample_topic, 'basics', sample_topic)
        RETURNING id INTO step_id_value;
        INSERT INTO learning_quiz(owner_id, roadmap_id, roadmap_step_id, topic, title)
        VALUES (learner.id, roadmap_id_value, step_id_value, sample_topic, '[OSC SAMPLE] ' || sample_topic)
        RETURNING id INTO quiz_id_value;
        question_text_value := CASE learner.username WHEN 'test1'
            THEN 'Pod 안의 컨테이너가 공유하는 자원은 무엇인가요?'
            ELSE '트랜잭션을 원자적으로 처리한다는 것은 무엇인가요?' END;
        answer_value := CASE learner.username WHEN 'test1' THEN '네트워크 네임스페이스'
            ELSE '전체 작업을 모두 반영하거나 모두 취소한다' END;
        wrong_value := CASE learner.username WHEN 'test1' THEN '모든 노드의 메모리'
            ELSE '모든 SQL을 병렬로 실행한다' END;
        INSERT INTO learning_question(quiz_id, question_order, question_text, options,
            correct_answer, explanation, option_explanations, source_references)
        VALUES (quiz_id_value, 1, question_text_value, json_build_array(answer_value, wrong_value)::text,
            answer_value,
            CASE learner.username WHEN 'test1' THEN '한 Pod의 컨테이너는 IP와 포트 공간을 공유합니다.'
                ELSE '원자성은 일부 변경만 남는 상태를 방지합니다.' END,
            json_build_array('이 선택지는 해당 개념의 핵심 정의에 맞습니다.',
                '이 선택지는 자원 공유 범위 또는 원자성의 의미를 잘못 설명합니다.')::text, '[]')
        RETURNING id INTO question_id_value;
        INSERT INTO learning_attempt(quiz_id, total_questions, correct_answers, submitted_at)
        VALUES (quiz_id_value, 1, CASE learner.username WHEN 'test1' THEN 0 ELSE 1 END,
            CURRENT_TIMESTAMP - INTERVAL '2 days') RETURNING id INTO attempt_id_value;
        INSERT INTO learning_attempt_answer(attempt_id, question_id, selected_answer, is_correct)
        VALUES (attempt_id_value, question_id_value,
            CASE learner.username WHEN 'test1' THEN wrong_value ELSE answer_value END,
            learner.username = 'test2');
        INSERT INTO review_schedule(question_id, next_review_at, status)
        VALUES (question_id_value, CURRENT_TIMESTAMP - INTERVAL '1 day', 'PENDING');
    END LOOP;
END $$;
COMMIT;
