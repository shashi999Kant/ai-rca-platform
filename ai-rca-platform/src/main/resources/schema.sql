-- Step 1a: Initialize critical extensions for vector math and UUIDs
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Step 1b: Core relational incident tracker table
CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(36) PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    root_service VARCHAR(100) NOT NULL,
    trigger_exception VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Step 1c: Structured RCA storage table
CREATE TABLE IF NOT EXISTS rca_reports (
    id VARCHAR(36) PRIMARY KEY,
    incident_id VARCHAR(36) REFERENCES incidents(id) ON DELETE CASCADE,
    root_cause TEXT NOT NULL,
    confidence_score INT NOT NULL,
    impacted_services TEXT,     -- Stored as comma-separated values for simplicity
    remediations TEXT,          -- Stored as semi-colon separated instruction items
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);