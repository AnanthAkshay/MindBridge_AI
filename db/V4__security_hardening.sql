-- ============================================
-- MindBridge AI - Security Hardening Migration
-- Step 10: Audit log, session TTL, anonymous
--          session support
-- ============================================

-- Audit log — tracks all sensitive operations
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    action          VARCHAR(100) NOT NULL,
    actor_id        BIGINT,                          -- NULL for anonymous actors
    target_id       BIGINT,
    ip_address      INET,
    user_agent      TEXT,
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Session TTL — configurable data retention (GDPR §17)
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

-- Allow anonymous sessions: make user_id nullable
ALTER TABLE sessions
    ALTER COLUMN user_id DROP NOT NULL;

-- Performance indexes for audit log
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at);

-- Index for expired session cleanup job
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at)
    WHERE expires_at IS NOT NULL;

-- ============================================
-- DOWN (reverse migration)
-- ============================================
-- DROP INDEX IF EXISTS idx_sessions_expires_at;
-- DROP INDEX IF EXISTS idx_audit_log_created_at;
-- DROP INDEX IF EXISTS idx_audit_log_actor;
-- DROP INDEX IF EXISTS idx_audit_log_action;
-- ALTER TABLE sessions ALTER COLUMN user_id SET NOT NULL;
-- ALTER TABLE sessions DROP COLUMN IF EXISTS expires_at;
-- DROP TABLE IF EXISTS audit_log;
