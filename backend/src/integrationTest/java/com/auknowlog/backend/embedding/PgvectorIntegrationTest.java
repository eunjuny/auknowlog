package com.auknowlog.backend.embedding;

import com.auknowlog.backend.embedding.repository.QuestionVectorRepository;
import com.auknowlog.backend.embedding.service.EmbeddingResult;
import com.auknowlog.backend.embedding.service.SemanticDuplicateService;
import com.auknowlog.backend.question.repository.QuestionHistoryRepository;
import com.auknowlog.backend.question.service.QuestionHistoryService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers
class PgvectorIntegrationTest {

    private static final int EMBEDDING_DIMENSIONS = 512;

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg16-bookworm")
    ).withDatabaseName("auknowlog_integration")
            .withUsername("auknowlog")
            .withPassword("auknowlog");

    private static JdbcTemplate jdbcTemplate;
    private static QuestionVectorRepository questionVectorRepository;

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        questionVectorRepository = new QuestionVectorRepository(jdbcTemplate);
    }

    @BeforeEach
    void resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE question_history RESTART IDENTITY CASCADE");
    }

    @Test
    void appliesFlywayMigrationsAndCreatesVectorHnswFeedbackReviewAndSourceSchema() {
        Integer successfulMigration = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '3' AND success = TRUE
                """, Integer.class);
        String extensionVersion = jdbcTemplate.queryForObject("""
                SELECT extversion FROM pg_extension WHERE extname = 'vector'
                """, String.class);
        String vectorColumnType = jdbcTemplate.queryForObject("""
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                JOIN pg_class table_info ON table_info.oid = attribute.attrelid
                WHERE table_info.relname = 'question_embedding'
                  AND attribute.attname = 'embedding'
                """, String.class);
        String indexDefinition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'idx_question_embedding_cosine'
                """, String.class);

        assertThat(successfulMigration).isEqualTo(1);
        assertThat(extensionVersion).startsWith("0.8.");
        assertThat(vectorColumnType).isEqualTo("vector(512)");
        assertThat(indexDefinition)
                .containsIgnoringCase("USING hnsw")
                .contains("vector_cosine_ops");

        Integer feedbackMigration = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '4' AND success = TRUE
                """, Integer.class);
        Integer feedbackTable = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'question_feedback'
                """, Integer.class);
        Integer feedbackQuestionConstraint = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname = 'uk_question_feedback_question'
                """, Integer.class);

        assertThat(feedbackMigration).isEqualTo(1);
        assertThat(feedbackTable).isEqualTo(1);
        assertThat(feedbackQuestionConstraint).isEqualTo(1);

        Integer roadmapMigration = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('5', '6', '7', '8', '9', '10') AND success = TRUE
                """, Integer.class);
        Integer roadmapTable = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'learning_roadmap'
                """, Integer.class);
        Integer roadmapLinkColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'learning_quiz'
                  AND column_name = 'roadmap_id'
                """, Integer.class);
        Integer roadmapStepTable = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'learning_roadmap_step'
                """, Integer.class);
        Integer roadmapStepDependencyTable = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'learning_roadmap_step_dependency'
                """, Integer.class);
        Integer roadmapStepLinkColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'learning_quiz'
                  AND column_name = 'roadmap_step_id'
                """, Integer.class);
        Integer roadmapMajorTopicColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'learning_roadmap_step'
                  AND column_name = 'major_topic_key'
                """, Integer.class);
        Integer roadmapSubtopicColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'learning_roadmap_step'
                  AND column_name = 'subtopic_key'
                """, Integer.class);
        Integer reviewAttemptTable = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'review_attempt'
                """, Integer.class);
        Integer pendingReviewIndex = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'uk_review_schedule_pending_question'
                """, Integer.class);

        assertThat(roadmapMigration).isEqualTo(6);
        assertThat(roadmapTable).isEqualTo(1);
        assertThat(roadmapLinkColumn).isEqualTo(1);
        assertThat(roadmapStepTable).isEqualTo(1);
        assertThat(roadmapStepDependencyTable).isEqualTo(1);
        assertThat(roadmapStepLinkColumn).isEqualTo(1);
        assertThat(roadmapMajorTopicColumn).isEqualTo(1);
        assertThat(roadmapSubtopicColumn).isEqualTo(1);
        assertThat(reviewAttemptTable).isEqualTo(1);
        assertThat(pendingReviewIndex).isEqualTo(1);

        Integer sourceMigration = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('11', '12') AND success = TRUE
                """, Integer.class);
        Integer sourceMetadataColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'source_document'
                  AND column_name IN ('source_type', 'source_uri', 'original_name', 'mime_type',
                                      'content_hash', 'processing_status', 'fetched_at')
                """, Integer.class);
        String sourceHashIndex = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'uk_source_document_content_hash'
                """, String.class);
        Integer roadmapSourceColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'learning_roadmap'
                  AND column_name = 'source_document_id'
                """, Integer.class);
        Integer roadmapSourceForeignKey = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint constraint_info
                JOIN pg_class table_info ON table_info.oid = constraint_info.conrelid
                WHERE table_info.relname = 'learning_roadmap'
                  AND constraint_info.contype = 'f'
                  AND pg_get_constraintdef(constraint_info.oid) LIKE '%source_document_id%'
                """, Integer.class);
        String roadmapSourceIndex = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'idx_learning_roadmap_source_document'
                """, String.class);

        assertThat(sourceMigration).isEqualTo(2);
        assertThat(sourceMetadataColumns).isEqualTo(7);
        assertThat(sourceHashIndex).containsIgnoringCase("UNIQUE").contains("content_hash");
        assertThat(roadmapSourceColumn).isEqualTo(1);
        assertThat(roadmapSourceForeignKey).isEqualTo(1);
        assertThat(roadmapSourceIndex).contains("source_document_id");
    }

    @Test
    void blocksSemanticallySimilarQuestionUsingRealPgvectorCosineSearch() {
        long questionHistoryId = insertQuestionHistory();
        questionVectorRepository.upsert(questionHistoryId,
                embedding("fixture-embedding", vector(1.0f, 0.0f)));

        SemanticDuplicateService similarQuestionService = semanticService(
                embedding("fixture-embedding", vector(0.99f, 0.10f)));
        SemanticDuplicateService differentQuestionService = semanticService(
                embedding("fixture-embedding", vector(0.0f, 1.0f)));

        SemanticDuplicateService.SemanticCheck similar = similarQuestionService.check("표현만 바꾼 JVM 문제");
        SemanticDuplicateService.SemanticCheck different = differentQuestionService.check("전혀 다른 SQL 문제");

        SemanticDuplicateService feedbackSensitiveService = semanticService(
                embedding("fixture-embedding", vector(0.85f, 0.5268f)));
        SemanticDuplicateService.SemanticCheck standardThreshold = feedbackSensitiveService.check("사용자가 반복적이라고 느낀 유사 문제");
        SemanticDuplicateService.SemanticCheck feedbackThreshold = feedbackSensitiveService.checkAgainstQuestionHashes(
                standardThreshold, java.util.List.of("a".repeat(64)), 0.82);

        assertThat(similar.duplicate()).isTrue();
        assertThat(similar.similarity()).isGreaterThanOrEqualTo(0.90);
        assertThat(different.duplicate()).isFalse();
        assertThat(standardThreshold.duplicate()).isFalse();
        assertThat(feedbackThreshold.duplicate()).isTrue();
        assertThat(feedbackThreshold.similarity()).isBetween(0.82, 0.90);
    }

    private long insertQuestionHistory() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO question_history
                    (topic, question_text, question_hash, options, correct_answer, explanation)
                VALUES
                    ('Java', 'JVM의 역할은 무엇인가요?', ?, '["A","B"]', 'A', '바이트코드를 실행합니다.')
                RETURNING id
                """, Long.class, "a".repeat(64));
    }

    private SemanticDuplicateService semanticService(EmbeddingResult embedding) {
        return new SemanticDuplicateService(
                ignored -> Optional.of(embedding),
                questionVectorRepository,
                mock(QuestionHistoryService.class),
                mock(QuestionHistoryRepository.class)
        );
    }

    private EmbeddingResult embedding(String model, float[] values) {
        return new EmbeddingResult(model, values, 0);
    }

    private float[] vector(float first, float second) {
        float[] values = new float[EMBEDDING_DIMENSIONS];
        Arrays.fill(values, 0.0f);
        values[0] = first;
        values[1] = second;
        return values;
    }
}
