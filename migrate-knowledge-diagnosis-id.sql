-- 为知识库增加来源诊断ID，支持同一工单多条大模型诊断分别入库
ALTER TABLE `ops_knowledge`
    ADD COLUMN `source_diagnosis_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源诊断ID(可选)' AFTER `source_ticket_id`,
    ADD KEY `idx_ops_knowledge_source_diagnosis_id` (`source_diagnosis_id`);
