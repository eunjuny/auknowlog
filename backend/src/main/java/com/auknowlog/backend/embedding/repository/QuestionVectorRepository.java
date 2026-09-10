package com.auknowlog.backend.embedding.repository;

import com.auknowlog.backend.embedding.service.EmbeddingResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class QuestionVectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public QuestionVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SimilarQuestion> findMostSimilar(EmbeddingResult embedding, double threshold) {
        String vector = toVectorLiteral(embedding.values());
        return jdbcTemplate.query("""
                        SELECT question_history_id, 1 - (embedding <=> CAST(? AS vector)) AS similarity
                        FROM question_embedding
                        WHERE 1 - (embedding <=> CAST(? AS vector)) >= ?
                        ORDER BY embedding <=> CAST(? AS vector)
                        LIMIT 1
                        """,
                (resultSet, rowNumber) -> new SimilarQuestion(
                        resultSet.getLong("question_history_id"), resultSet.getDouble("similarity")),
                vector, vector, threshold, vector
        ).stream().findFirst();
    }

    public Optional<SimilarQuestion> findMostSimilarByQuestionHashes(
            EmbeddingResult embedding,
            List<String> questionHashes,
            double threshold
    ) {
        if (questionHashes == null || questionHashes.isEmpty()) {
            return Optional.empty();
        }

        String vector = toVectorLiteral(embedding.values());
        String placeholders = String.join(",", Collections.nCopies(questionHashes.size(), "?"));
        String sql = """
                SELECT embedding.question_history_id,
                       1 - (embedding.embedding <=> CAST(? AS vector)) AS similarity
                FROM question_embedding embedding
                JOIN question_history history ON history.id = embedding.question_history_id
                WHERE history.question_hash IN (%s)
                  AND 1 - (embedding.embedding <=> CAST(? AS vector)) >= ?
                ORDER BY embedding.embedding <=> CAST(? AS vector)
                LIMIT 1
                """.formatted(placeholders);

        List<Object> arguments = new ArrayList<>();
        arguments.add(vector);
        arguments.addAll(questionHashes);
        arguments.add(vector);
        arguments.add(threshold);
        arguments.add(vector);

        return jdbcTemplate.query(sql,
                (resultSet, rowNumber) -> new SimilarQuestion(
                        resultSet.getLong("question_history_id"), resultSet.getDouble("similarity")),
                arguments.toArray()
        ).stream().findFirst();
    }

    public void upsert(long questionHistoryId, EmbeddingResult embedding) {
        jdbcTemplate.update("""
                        INSERT INTO question_embedding (question_history_id, embedding, embedding_model)
                        VALUES (?, CAST(? AS vector), ?)
                        ON CONFLICT (question_history_id)
                        DO UPDATE SET embedding = EXCLUDED.embedding,
                                      embedding_model = EXCLUDED.embedding_model,
                                      created_at = CURRENT_TIMESTAMP
                        """,
                questionHistoryId, toVectorLiteral(embedding.values()), embedding.model());
    }

    private String toVectorLiteral(float[] values) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(values[index]);
        }
        return literal.append(']').toString();
    }

    public record SimilarQuestion(long questionHistoryId, double similarity) {
    }
}
