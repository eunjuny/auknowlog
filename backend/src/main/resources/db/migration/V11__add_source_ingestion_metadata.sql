ALTER TABLE source_document
    ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN source_uri VARCHAR(2048),
    ADD COLUMN original_name VARCHAR(255),
    ADD COLUMN mime_type VARCHAR(128),
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN processing_status VARCHAR(32) NOT NULL DEFAULT 'READY',
    ADD COLUMN fetched_at TIMESTAMP;

CREATE UNIQUE INDEX uk_source_document_content_hash
    ON source_document (content_hash)
    WHERE content_hash IS NOT NULL;

CREATE INDEX idx_source_document_type_created
    ON source_document (source_type, created_at DESC);
