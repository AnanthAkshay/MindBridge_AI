-- ============================================
-- MindBridge AI - Escalation Engine Migration
-- Step 9: Escalation log, therapist queue,
--         and notifications outbox tables
-- ============================================

-- Escalation log — records every crisis escalation event
CREATE TABLE IF NOT EXISTS escalation_log (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT       NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id         BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    trigger_rule    VARCHAR(50)  NOT NULL CHECK (trigger_rule IN ('consecutive_3', 'single_85')),
    risk_score      INTEGER      NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP WITH TIME ZONE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Therapist queue — pending escalations for therapist review
CREATE TABLE IF NOT EXISTS therapist_queue (
    id              BIGSERIAL PRIMARY KEY,
    escalation_id   BIGINT       NOT NULL REFERENCES escalation_log(id) ON DELETE CASCADE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'reviewed', 'closed')),
    assigned_to     VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Notifications outbox — simulated email/SMS payloads for future delivery
CREATE TABLE IF NOT EXISTS notifications_outbox (
    id              BIGSERIAL PRIMARY KEY,
    escalation_id   BIGINT       NOT NULL REFERENCES escalation_log(id) ON DELETE CASCADE,
    channel         VARCHAR(20)  NOT NULL CHECK (channel IN ('email', 'sms')),
    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(255),
    payload         TEXT         NOT NULL,
    sent            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_escalation_log_session ON escalation_log(session_id);
CREATE INDEX IF NOT EXISTS idx_escalation_log_user ON escalation_log(user_id);
CREATE INDEX IF NOT EXISTS idx_escalation_log_active ON escalation_log(session_id, is_active);
CREATE INDEX IF NOT EXISTS idx_therapist_queue_status ON therapist_queue(status);
CREATE INDEX IF NOT EXISTS idx_therapist_queue_escalation ON therapist_queue(escalation_id);
CREATE INDEX IF NOT EXISTS idx_notifications_outbox_sent ON notifications_outbox(sent);

-- Add risk_score column to messages for per-message risk tracking
ALTER TABLE messages ADD COLUMN IF NOT EXISTS risk_score INTEGER;
