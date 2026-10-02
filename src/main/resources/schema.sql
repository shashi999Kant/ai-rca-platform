-- Extensions for vector math and UUIDs
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- One row per error received (duplicates by trace_id are rejected by the unique index)
CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(36) PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    root_service VARCHAR(100) NOT NULL,
    trigger_exception VARCHAR(255) NOT NULL,
    message TEXT,
    error_signature VARCHAR(64) NOT NULL,
    duplicate_of VARCHAR(36) REFERENCES incidents(id), -- set when an earlier incident's report is reused
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',       -- OPEN -> ANALYZING -> DONE / FAILED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_incidents_trace_id ON incidents(trace_id);
CREATE INDEX IF NOT EXISTS ix_incidents_signature ON incidents(error_signature, created_at);

-- One structured RCA report per analysed incident
CREATE TABLE IF NOT EXISTS rca_reports (
    id VARCHAR(36) PRIMARY KEY,
    incident_id VARCHAR(36) NOT NULL UNIQUE REFERENCES incidents(id) ON DELETE CASCADE,
    root_cause TEXT NOT NULL,
    confidence_score INT NOT NULL,
    impacted_services TEXT,     -- comma-separated
    remediations TEXT,          -- semicolon-separated
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
