-- ─────────────────────────────────────────────────────────────────────────────
-- V5: Create audit_logs table
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE audit_logs (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id         VARCHAR(36),
    action          VARCHAR(50)  NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       VARCHAR(36),
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    request_details TEXT,
    result          VARCHAR(10)  NOT NULL,
    failure_reason  VARCHAR(500),
    timestamp       TIMESTAMP    NOT NULL DEFAULT now(),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),

    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT chk_audit_logs_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

COMMENT ON TABLE  audit_logs IS 'Immutable audit trail. Written asynchronously after every significant business and security event.';
COMMENT ON COLUMN audit_logs.request_details IS 'Sanitized JSON of request parameters. Sensitive fields (passwords, tokens, full account numbers) are masked before insertion.';
COMMENT ON COLUMN audit_logs.result IS 'Coarse outcome: SUCCESS or FAILURE. Details in failure_reason.';
