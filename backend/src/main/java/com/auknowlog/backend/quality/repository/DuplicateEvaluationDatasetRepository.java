package com.auknowlog.backend.quality.repository;

import com.auknowlog.backend.embedding.service.EmbeddingResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DuplicateEvaluationDatasetRepository {

    private final JdbcTemplate jdbcTemplate;

    public DuplicateEvaluationDatasetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DatasetRow> findByKey(String datasetKey) {
        return jdbcTemplate.query("""
                SELECT id, dataset_key, title, dataset_version, source_type, status,
                       embedding_model, embedding_input_tokens, created_at, embedded_at
                FROM duplicate_evaluation_dataset
                WHERE dataset_key = ?
                """, (resultSet, rowNumber) -> datasetRow(resultSet), datasetKey)
                .stream().findFirst();
    }

    public Optional<DatasetRow> findById(long datasetId) {
        return jdbcTemplate.query("""
                SELECT id, dataset_key, title, dataset_version, source_type, status,
                       embedding_model, embedding_input_tokens, created_at, embedded_at
                FROM duplicate_evaluation_dataset
                WHERE id = ?
                """, (resultSet, rowNumber) -> datasetRow(resultSet), datasetId)
                .stream().findFirst();
    }

    public long createDataset(String datasetKey, String title, String datasetVersion, int sampleCount) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO duplicate_evaluation_dataset (
                    dataset_key, title, dataset_version, source_type, status
                ) VALUES (?, ?, ?, 'CURATED_REFERENCE', 'DRAFT')
                RETURNING id
                """, Long.class, datasetKey, title, datasetVersion);
        if (id == null) {
            throw new IllegalStateException("중복 평가 데이터셋을 저장하지 못했습니다.");
        }
        return id;
    }

    public void insertSample(long datasetId, int sampleOrder, String topic, String questionA, String questionB,
                             String referenceVerdict, String referenceRationale) {
        jdbcTemplate.update("""
                INSERT INTO duplicate_evaluation_dataset_sample (
                    dataset_id, sample_order, topic, question_a, question_b,
                    reference_verdict, reference_rationale
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, datasetId, sampleOrder, topic, questionA, questionB, referenceVerdict, referenceRationale);
    }

    public List<DatasetSummaryRow> findAllSummaries() {
        return jdbcTemplate.query("""
                SELECT dataset.id, dataset.dataset_key, dataset.title, dataset.dataset_version,
                       dataset.source_type, dataset.status, dataset.embedding_model,
                       dataset.embedding_input_tokens,
                       COUNT(sample.id) AS total_samples,
                       COALESCE(SUM(CASE WHEN sample.similarity IS NOT NULL THEN 1 ELSE 0 END), 0) AS embedded_samples,
                       COALESCE(SUM(CASE WHEN sample.reference_verdict = 'DUPLICATE' THEN 1 ELSE 0 END), 0) AS duplicate_samples,
                       COALESCE(SUM(CASE WHEN sample.reference_verdict = 'RELATED' THEN 1 ELSE 0 END), 0) AS related_samples,
                       COALESCE(SUM(CASE WHEN sample.reference_verdict = 'DISTINCT' THEN 1 ELSE 0 END), 0) AS distinct_samples
                FROM duplicate_evaluation_dataset dataset
                LEFT JOIN duplicate_evaluation_dataset_sample sample ON sample.dataset_id = dataset.id
                GROUP BY dataset.id, dataset.dataset_key, dataset.title, dataset.dataset_version,
                         dataset.source_type, dataset.status, dataset.embedding_model, dataset.embedding_input_tokens
                ORDER BY dataset.created_at DESC, dataset.id DESC
                """, (resultSet, rowNumber) -> new DatasetSummaryRow(
                resultSet.getLong("id"), resultSet.getString("dataset_key"), resultSet.getString("title"),
                resultSet.getString("dataset_version"), resultSet.getString("source_type"),
                resultSet.getString("status"), resultSet.getString("embedding_model"),
                resultSet.getLong("embedding_input_tokens"), resultSet.getInt("total_samples"),
                resultSet.getInt("embedded_samples"), resultSet.getInt("duplicate_samples"),
                resultSet.getInt("related_samples"), resultSet.getInt("distinct_samples")
        ));
    }

    public List<DatasetSampleRow> findPendingEmbeddings(long datasetId) {
        return jdbcTemplate.query("""
                SELECT id, sample_order, topic, question_a, question_b, reference_verdict, reference_rationale
                FROM duplicate_evaluation_dataset_sample
                WHERE dataset_id = ? AND similarity IS NULL
                ORDER BY sample_order
                """, (resultSet, rowNumber) -> new DatasetSampleRow(
                resultSet.getLong("id"), resultSet.getInt("sample_order"), resultSet.getString("topic"),
                resultSet.getString("question_a"), resultSet.getString("question_b"),
                resultSet.getString("reference_verdict"), resultSet.getString("reference_rationale")
        ), datasetId);
    }

    public List<LabeledSimilaritySample> findEmbeddedSamples(long datasetId) {
        return jdbcTemplate.query("""
                SELECT id, reference_verdict, similarity
                FROM duplicate_evaluation_dataset_sample
                WHERE dataset_id = ? AND similarity IS NOT NULL
                ORDER BY sample_order
                """, (resultSet, rowNumber) -> new LabeledSimilaritySample(
                resultSet.getLong("id"), resultSet.getString("reference_verdict"), resultSet.getDouble("similarity")
        ), datasetId);
    }

    public int reviewSample(long datasetId, int sampleOrder, String reviewerVerdict) {
        return jdbcTemplate.update("""
                UPDATE duplicate_evaluation_dataset_sample
                SET reviewer_verdict = ?, reviewed_at = CURRENT_TIMESTAMP
                WHERE dataset_id = ? AND sample_order = ?
                """, reviewerVerdict, datasetId, sampleOrder);
    }

    public void markProcessing(long datasetId) {
        jdbcTemplate.update("""
                UPDATE duplicate_evaluation_dataset
                SET status = 'PROCESSING'
                WHERE id = ?
                """, datasetId);
    }

    public void saveEmbedding(long sampleId, EmbeddingResult first, EmbeddingResult second) {
        String firstVector = vectorLiteral(first.values());
        String secondVector = vectorLiteral(second.values());
        jdbcTemplate.update("""
                UPDATE duplicate_evaluation_dataset_sample
                SET embedding_a = CAST(? AS vector), embedding_b = CAST(? AS vector),
                    similarity = 1 - (CAST(? AS vector) <=> CAST(? AS vector)),
                    embedding_model = ?
                WHERE id = ?
                """, firstVector, secondVector, firstVector, secondVector, first.model(), sampleId);
    }

    public void markReady(long datasetId, String model, long inputTokens) {
        jdbcTemplate.update("""
                UPDATE duplicate_evaluation_dataset
                SET status = 'READY', embedding_model = ?, embedding_input_tokens = ?,
                    embedded_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, model, inputTokens, datasetId);
    }

    public void markFailed(long datasetId) {
        jdbcTemplate.update("""
                UPDATE duplicate_evaluation_dataset
                SET status = 'FAILED'
                WHERE id = ?
                """, datasetId);
    }

    private DatasetRow datasetRow(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new DatasetRow(
                resultSet.getLong("id"), resultSet.getString("dataset_key"), resultSet.getString("title"),
                resultSet.getString("dataset_version"), resultSet.getString("source_type"),
                resultSet.getString("status"), resultSet.getString("embedding_model"),
                resultSet.getLong("embedding_input_tokens"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("embedded_at") == null ? null : resultSet.getTimestamp("embedded_at").toLocalDateTime()
        );
    }

    private String vectorLiteral(float[] values) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) literal.append(',');
            literal.append(values[index]);
        }
        return literal.append(']').toString();
    }

    public record DatasetRow(long id, String datasetKey, String title, String datasetVersion,
                             String sourceType, String status, String embeddingModel, long embeddingInputTokens,
                             LocalDateTime createdAt, LocalDateTime embeddedAt) {
    }

    public record DatasetSummaryRow(long id, String datasetKey, String title, String datasetVersion,
                                    String sourceType, String status, String embeddingModel,
                                    long embeddingInputTokens, int totalSamples, int embeddedSamples,
                                    int duplicateSamples, int relatedSamples, int distinctSamples) {
    }

    public record DatasetSampleRow(long id, int sampleOrder, String topic, String questionA, String questionB,
                                   String referenceVerdict, String referenceRationale) {
    }

    public record LabeledSimilaritySample(long sampleId, String referenceVerdict, double similarity) {
    }
}
