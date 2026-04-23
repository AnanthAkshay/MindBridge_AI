-- ============================================
-- MindBridge AI - Database Schema (Production-Grade)
-- PostgreSQL 16
-- ============================================

-- Users table (supports both registered + anonymous users)
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) UNIQUE,          -- nullable for anonymous users
    password_hash   VARCHAR(255),                  -- nullable for anonymous users
    full_name       VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(512),
    role            VARCHAR(50)  NOT NULL DEFAULT 'USER',
    is_anonymous    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Refresh tokens for JWT rotation
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(512) NOT NULL UNIQUE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Sessions table (therapy / chat sessions)
CREATE TABLE IF NOT EXISTS sessions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255),
    session_type    VARCHAR(50)  NOT NULL DEFAULT 'CHAT',
    mood_score      INTEGER      CHECK (mood_score >= 1 AND mood_score <= 10),
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    risk_score      INTEGER      DEFAULT 0,
    risk_level      VARCHAR(20)  DEFAULT 'LOW',
    risk_updated_at TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE
);

-- Messages table (AES-256-GCM encrypted at rest)
CREATE TABLE IF NOT EXISTS messages (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT       NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    sender_type         VARCHAR(20)  NOT NULL CHECK (sender_type IN ('USER', 'AI')),
    encrypted_content   TEXT         NOT NULL,
    encryption_iv       VARCHAR(64)  NOT NULL,
    emotion             VARCHAR(50),
    emotion_score       DOUBLE PRECISION,
    valence             DOUBLE PRECISION,
    arousal             DOUBLE PRECISION,
    risk_score          INTEGER,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Emotion Memory table (Intelligent cross-session context)
CREATE TABLE IF NOT EXISTS emotion_memory (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id          BIGINT       NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    dominant_emotion    VARCHAR(50)  NOT NULL,
    confidence          DOUBLE PRECISION,
    valence             DOUBLE PRECISION,
    arousal             DOUBLE PRECISION,
    summary_text        TEXT,
    trigger_tag         VARCHAR(100),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Audit log table (Security compliance)
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    action          VARCHAR(100) NOT NULL,
    actor_id        BIGINT,
    target_id       BIGINT,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    metadata        TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Escalation log table (Crisis management)
CREATE TABLE IF NOT EXISTS escalation_log (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT       NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id         BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    trigger_rule    VARCHAR(50)  NOT NULL,
    risk_score      INTEGER      NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP WITH TIME ZONE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Notifications outbox table (Transactional messaging)
CREATE TABLE IF NOT EXISTS notifications_outbox (
    id              BIGSERIAL PRIMARY KEY,
    escalation_id   BIGINT       NOT NULL REFERENCES escalation_log(id) ON DELETE CASCADE,
    channel         VARCHAR(20)  NOT NULL,
    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(255),
    payload         TEXT         NOT NULL,
    sent            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Therapist queue table (Review workflow)
CREATE TABLE IF NOT EXISTS therapist_queue (
    id              BIGSERIAL PRIMARY KEY,
    escalation_id   BIGINT       NOT NULL REFERENCES escalation_log(id) ON DELETE CASCADE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
    assigned_to     VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Recommendation tracking for clinical interventions
CREATE TABLE IF NOT EXISTS recommendation_logs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id          BIGINT       REFERENCES sessions(id) ON DELETE CASCADE,
    content_id          VARCHAR(100) NOT NULL,
    content_type        VARCHAR(50)  NOT NULL,
    completed           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP WITH TIME ZONE
);

-- OTP Codes table for passwordless login
CREATE TABLE IF NOT EXISTS otp_codes (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    code            VARCHAR(10)  NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_anonymous ON users(is_anonymous);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
CREATE INDEX IF NOT EXISTS idx_messages_session_id ON messages(session_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at);
CREATE INDEX IF NOT EXISTS idx_otp_codes_email ON otp_codes(email);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_id);
CREATE INDEX IF NOT EXISTS idx_escalation_log_session ON escalation_log(session_id);

-- Seed a demo user (password: "Demo1234!" - BCrypt strength 12)
INSERT INTO users (email, password_hash, full_name, role, is_anonymous)
VALUES ('demo@mindbridge.ai', '$2a$12$LJ3m4ys3QzGBNkEOvq2fNOBpg5v8.UZJaRqfGI8WxIVTXVL6VxKGy', 'Alex Morgan', 'USER', FALSE)
ON CONFLICT (email) DO NOTHING;
