CREATE TABLE admin_audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT REFERENCES app_user(id),
    target_user_id BIGINT REFERENCES app_user(id),
    action VARCHAR(300) NOT NULL,
    response_status INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_admin_audit_created ON admin_audit_log(created_at DESC);
CREATE INDEX idx_admin_audit_target_created ON admin_audit_log(target_user_id, created_at DESC);

ALTER TABLE app_user ADD COLUMN ai_daily_token_limit BIGINT NOT NULL DEFAULT 20000 CHECK (ai_daily_token_limit >= 0);
ALTER TABLE app_user ADD COLUMN ai_daily_call_limit INTEGER NOT NULL DEFAULT 100 CHECK (ai_daily_call_limit >= 0);
CREATE TABLE ai_budget_bucket (
    budget_date DATE NOT NULL,
    scope VARCHAR(64) NOT NULL,
    used_tokens BIGINT NOT NULL DEFAULT 0,
    reserved_tokens BIGINT NOT NULL DEFAULT 0,
    started_calls INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (budget_date, scope),
    CHECK (used_tokens >= 0 AND reserved_tokens >= 0)
);
CREATE TABLE ai_budget_reservation (
    id UUID PRIMARY KEY,
    owner_id BIGINT REFERENCES app_user(id),
    budget_date DATE NOT NULL,
    estimated_tokens BIGINT NOT NULL,
    actual_tokens BIGINT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP
);
CREATE INDEX idx_ai_reservation_owner_date ON ai_budget_reservation(owner_id, budget_date);

CREATE TABLE user_notification_setting (
    owner_id BIGINT PRIMARY KEY REFERENCES app_user(id),
    email VARCHAR(254),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    review_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    daily_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    send_hour INTEGER NOT NULL DEFAULT 8 CHECK (send_hour BETWEEN 0 AND 23),
    version BIGINT NOT NULL DEFAULT 0,
    verification_hash VARCHAR(64),
    verification_expires_at TIMESTAMP,
    verification_requested_at TIMESTAMP,
    verification_attempts INTEGER NOT NULL DEFAULT 0,
    verification_day DATE,
    verification_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE learning_notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES app_user(id),
    settings_version BIGINT NOT NULL,
    dedup_key VARCHAR(160) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    manual_retries INTEGER NOT NULL DEFAULT 0,
    failure_type VARCHAR(64),
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_learning_notification_due ON learning_notification_outbox(status, available_at);
CREATE INDEX idx_learning_notification_owner_created ON learning_notification_outbox(owner_id, created_at DESC);
