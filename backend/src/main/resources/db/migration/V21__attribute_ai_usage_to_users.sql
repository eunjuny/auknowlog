-- Old records cannot reliably be attributed. NULL means legacy or system work.
ALTER TABLE ai_generation_log ADD COLUMN owner_id BIGINT REFERENCES app_user(id);
CREATE INDEX idx_ai_generation_log_owner_created ON ai_generation_log(owner_id, created_at DESC);
