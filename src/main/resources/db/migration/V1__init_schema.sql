CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- users
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    user_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    role         VARCHAR(50)  NOT NULL CHECK (role IN ('EMPLOYEE','IT_ADMIN','AUDITOR')),
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- files
-- ----------------------------------------------------------------------------
CREATE TABLE files (
    file_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(user_id),
    file_name         VARCHAR(500) NOT NULL,
    source_path       VARCHAR(1000) NOT NULL,
    retention_policy  VARCHAR(50)  NOT NULL DEFAULT 'DEFAULT',
    storage_location  VARCHAR(500),
    current_version_id UUID,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0
);

-- ----------------------------------------------------------------------------
-- file_versions
-- ----------------------------------------------------------------------------
CREATE TABLE file_versions (
    version_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id           UUID         NOT NULL REFERENCES files(file_id),
    version_number    INT          NOT NULL,
    checksum          VARCHAR(255) NOT NULL,
    size_bytes        BIGINT       NOT NULL DEFAULT 0,
    state             VARCHAR(50)  NOT NULL DEFAULT 'COMMITTED'
                        CHECK (state IN ('REQUESTED','UPLOADING','COMMITTED','REJECTED','EXPIRED')),
    merkle_root       VARCHAR(255),
    chunk_refs        JSONB,
    dedup_result_id   VARCHAR(255),
    retention_policy  VARCHAR(50)  NOT NULL DEFAULT 'DEFAULT',
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (file_id, version_number)
);

ALTER TABLE files
    ADD CONSTRAINT fk_files_current_version
    FOREIGN KEY (current_version_id) REFERENCES file_versions(version_id);

-- ----------------------------------------------------------------------------
-- backups
-- ----------------------------------------------------------------------------
CREATE TABLE backups (
    backup_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(user_id),
    file_id             UUID         NOT NULL REFERENCES files(file_id),
    version_id          UUID                  REFERENCES file_versions(version_id),
    backup_type         VARCHAR(50)  NOT NULL,
    state               VARCHAR(50)  NOT NULL
                          CHECK (state IN ('REQUESTED','QUEUED','CHUNKING','DEDUPLICATING',
                                           'UPLOADING','COMMITTED','VERIFIED','COMPLETED',
                                           'REJECTED','PARTIAL','RETRYING','CANCELLED',
                                           'EXPIRED','COMPENSATION_REQUIRED')),
    priority            VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    correlation_id      VARCHAR(255) NOT NULL,
    lease_id            VARCHAR(255),
    decision_id         VARCHAR(255),
    dedup_result_id     VARCHAR(255),
    verification_status VARCHAR(50)  NOT NULL DEFAULT 'PENDING'
                          CHECK (verification_status IN ('PENDING','VERIFIED','DEGRADED','FAILED')),
    failure_reason      VARCHAR(1000),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);

-- ----------------------------------------------------------------------------
-- restores
-- ----------------------------------------------------------------------------
CREATE TABLE restores (
    restore_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    backup_id         UUID         NOT NULL REFERENCES backups(backup_id),
    version_id        UUID         NOT NULL REFERENCES file_versions(version_id),
    user_id           UUID         NOT NULL REFERENCES users(user_id),
    state             VARCHAR(50)  NOT NULL
                        CHECK (state IN ('REQUESTED','LEASED','COMMITTED','RESTORED',
                                         'REJECTED','CANCELLED','COMPENSATION_REQUIRED')),
    lease_id          VARCHAR(255),
    idempotency_key   VARCHAR(255) NOT NULL UNIQUE,
    correlation_id    VARCHAR(255) NOT NULL,
    failure_reason    VARCHAR(1000),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0
);

-- ----------------------------------------------------------------------------
-- audit_log  (append-only - never updated, never deleted by app code)
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log (
    audit_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor           VARCHAR(255) NOT NULL,
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(255) NOT NULL,
    before_value    JSONB,
    after_value     JSONB,
    correlation_id  VARCHAR(255) NOT NULL,
    source_service  VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- indexes
-- ----------------------------------------------------------------------------
CREATE INDEX idx_files_user            ON files(user_id);
CREATE INDEX idx_versions_file         ON file_versions(file_id);
CREATE INDEX idx_backups_file          ON backups(file_id);
CREATE INDEX idx_backups_user          ON backups(user_id);
CREATE INDEX idx_backups_correlation   ON backups(correlation_id);
CREATE INDEX idx_backups_state         ON backups(state);
CREATE INDEX idx_backups_idem          ON backups(idempotency_key);
CREATE INDEX idx_restores_backup       ON restores(backup_id);
CREATE INDEX idx_restores_correlation  ON restores(correlation_id);
CREATE INDEX idx_restores_idem         ON restores(idempotency_key);
CREATE INDEX idx_audit_correlation     ON audit_log(correlation_id);
CREATE INDEX idx_audit_entity          ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_created_at      ON audit_log(created_at);
CREATE INDEX idx_audit_action          ON audit_log(action);
