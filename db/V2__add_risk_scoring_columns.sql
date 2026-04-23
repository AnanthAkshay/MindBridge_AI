-- ============================================
-- MindBridge AI - Risk Scoring Migration
-- Step 8: Add risk scoring columns to sessions
-- ============================================

-- Add risk_score column (integer 0-100)
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS risk_score INTEGER DEFAULT 0
    CHECK (risk_score >= 0 AND risk_score <= 100);

-- Add risk_level column (enum LOW/MODERATE/HIGH)
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20) DEFAULT 'LOW'
    CHECK (risk_level IN ('LOW', 'MODERATE', 'HIGH'));

-- Add risk_updated_at timestamp column
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS risk_updated_at TIMESTAMP WITH TIME ZONE;

-- Performance index for querying recent high-risk sessions
CREATE INDEX IF NOT EXISTS idx_sessions_risk_level ON sessions(risk_level);
CREATE INDEX IF NOT EXISTS idx_sessions_risk_updated_at ON sessions(risk_updated_at);
CREATE INDEX IF NOT EXISTS idx_sessions_user_risk ON sessions(user_id, risk_level, risk_updated_at);
