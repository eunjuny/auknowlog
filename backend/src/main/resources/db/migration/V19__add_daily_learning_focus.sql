ALTER TABLE daily_learning
    ADD COLUMN focus_tier VARCHAR(32) NOT NULL DEFAULT 'IT_EXPANSION';

ALTER TABLE daily_learning
    ADD CONSTRAINT ck_daily_learning_focus_tier
        CHECK (focus_tier IN ('DEVELOPER_CORE', 'DEVELOPER_ADJACENT', 'IT_EXPANSION', 'USER_SELECTED'));
