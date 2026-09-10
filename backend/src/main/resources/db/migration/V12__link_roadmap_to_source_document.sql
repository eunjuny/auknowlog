ALTER TABLE learning_roadmap
    ADD COLUMN source_document_id BIGINT REFERENCES source_document(id) ON DELETE SET NULL;

CREATE INDEX idx_learning_roadmap_source_document
    ON learning_roadmap (source_document_id);
