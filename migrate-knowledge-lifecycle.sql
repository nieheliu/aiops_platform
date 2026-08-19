USE ops_ai_platform;

ALTER TABLE ops_knowledge
    ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' AFTER source_diagnosis_id,
    ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER lifecycle_status,
    ADD COLUMN component VARCHAR(64) DEFAULT 'other' AFTER version,
    ADD COLUMN entry_source VARCHAR(64) DEFAULT NULL AFTER component,
    ADD COLUMN created_by BIGINT UNSIGNED DEFAULT NULL AFTER entry_source,
    ADD COLUMN updated_by BIGINT UNSIGNED DEFAULT NULL AFTER created_by,
    ADD COLUMN reviewed_by BIGINT UNSIGNED DEFAULT NULL AFTER updated_by,
    ADD COLUMN reviewed_at DATETIME DEFAULT NULL AFTER reviewed_by,
    ADD KEY idx_ops_knowledge_lifecycle_status (lifecycle_status),
    ADD KEY idx_ops_knowledge_component (component),
    ADD KEY idx_ops_knowledge_entry_source (entry_source);

UPDATE ops_knowledge
SET entry_source = CASE
    WHEN source_diagnosis_id IS NOT NULL THEN 'diagnosis_import'
    WHEN source_ticket_id IS NOT NULL THEN 'ticket_resolve'
    ELSE 'manual_import'
END
WHERE entry_source IS NULL;

DROP TABLE IF EXISTS ops_knowledge_audit_log;
CREATE TABLE ops_knowledge_audit_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  knowledge_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(32) NOT NULL,
  operator_id BIGINT UNSIGNED DEFAULT NULL,
  operator_name VARCHAR(64) DEFAULT NULL,
  from_status VARCHAR(32) DEFAULT NULL,
  to_status VARCHAR(32) DEFAULT NULL,
  version INT DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  operate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ops_knowledge_audit_knowledge_id (knowledge_id),
  KEY idx_ops_knowledge_audit_operate_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
