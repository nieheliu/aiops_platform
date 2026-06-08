-- 建议先创建数据库并选择
-- CREATE DATABASE IF NOT EXISTS ops_ai_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE ops_ai_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
-- 1) 用户信息表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码(加密后)',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用,1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  UNIQUE KEY `uk_sys_user_email` (`email`),
  KEY `idx_sys_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';
-- 2) 角色定义表
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
  `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码，如ADMIN、DEV',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_role_code` (`role_code`),
  UNIQUE KEY `uk_sys_role_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色定义表';
-- 3) 用户角色关联表（多对多）
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `grant_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_sys_user_role_role_id` (`role_id`),
  CONSTRAINT `fk_sys_user_role_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_sys_user_role_role_id` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';
-- 4) 告警事件表
DROP TABLE IF EXISTS `ops_alert`;
CREATE TABLE `ops_alert` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '告警ID',
  `alert_name` VARCHAR(128) NOT NULL COMMENT '告警名称',
  `severity` TINYINT NOT NULL COMMENT '严重等级: 1-低,2-中,3-高,4-紧急',
  `instance_ip` VARCHAR(45) NOT NULL COMMENT '实例IP(兼容IPv4/IPv6)',
  `raw_payload` JSON DEFAULT NULL COMMENT '原始告警载荷(JSON)',
  `trigger_time` DATETIME NOT NULL COMMENT '触发时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_alert_severity` (`severity`),
  KEY `idx_ops_alert_instance_ip` (`instance_ip`),
  KEY `idx_ops_alert_trigger_time` (`trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警事件表';
-- 5) 运维工单表
DROP TABLE IF EXISTS `ops_ticket`;
CREATE TABLE `ops_ticket` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工单ID',
  `alert_id` BIGINT UNSIGNED NOT NULL COMMENT '关联告警ID',
  `handler_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '处理人用户ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理,1-处理中,2-已解决',
  `title` VARCHAR(200) NOT NULL COMMENT '工单标题',
  `description` TEXT DEFAULT NULL COMMENT '工单描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `resolve_time` DATETIME DEFAULT NULL COMMENT '解决时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_ticket_alert_id` (`alert_id`),
  KEY `idx_ops_ticket_handler_user_id` (`handler_user_id`),
  KEY `idx_ops_ticket_status` (`status`),
  CONSTRAINT `fk_ops_ticket_alert_id` FOREIGN KEY (`alert_id`) REFERENCES `ops_alert` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_ops_ticket_handler_user_id` FOREIGN KEY (`handler_user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运维工单表';
-- 6) 工单操作日志表
DROP TABLE IF EXISTS `ops_ticket_log`;
CREATE TABLE `ops_ticket_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `ticket_id` BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人用户ID',
  `action` VARCHAR(64) NOT NULL COMMENT '操作动作(如CREATE/ASSIGN/RESOLVE/COMMENT)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `operate_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_ticket_log_ticket_id` (`ticket_id`),
  KEY `idx_ops_ticket_log_operator_id` (`operator_id`),
  KEY `idx_ops_ticket_log_operate_time` (`operate_time`),
  CONSTRAINT `fk_ops_ticket_log_ticket_id` FOREIGN KEY (`ticket_id`) REFERENCES `ops_ticket` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_ops_ticket_log_operator_id` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单操作日志表';
-- 7) AI诊断报告表
DROP TABLE IF EXISTS `ai_diagnosis`;
CREATE TABLE `ai_diagnosis` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '诊断报告ID',
  `alert_id` BIGINT UNSIGNED NOT NULL COMMENT '关联告警ID',
  `ticket_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单ID',
  `ai_model` VARCHAR(100) NOT NULL COMMENT 'AI模型名称',
  `root_cause_analysis` TEXT NOT NULL COMMENT '根因分析',
  `suggested_fix` TEXT DEFAULT NULL COMMENT '修复建议',
  `confidence_score` DECIMAL(5,2) DEFAULT NULL COMMENT '置信度(0-100)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_diagnosis_alert_id` (`alert_id`),
  KEY `idx_ai_diagnosis_ticket_id` (`ticket_id`),
  KEY `idx_ai_diagnosis_model` (`ai_model`),
  CONSTRAINT `fk_ai_diagnosis_alert_id` FOREIGN KEY (`alert_id`) REFERENCES `ops_alert` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_ai_diagnosis_ticket_id` FOREIGN KEY (`ticket_id`) REFERENCES `ops_ticket` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI诊断报告表';
-- 8) 知识库文档表
DROP TABLE IF EXISTS `ops_knowledge`;
CREATE TABLE `ops_knowledge` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识文档ID',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content_md` MEDIUMTEXT NOT NULL COMMENT 'Markdown内容',
  `tags` JSON DEFAULT NULL COMMENT '标签(JSON数组)，如["mysql","network"]',
  `sync_es_status` TINYINT NOT NULL DEFAULT 0 COMMENT 'ES同步状态: 0-未同步,1-已同步,2-同步失败',
  `source_alert_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源告警ID(可选)',
  `source_ticket_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源工单ID(可选)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_knowledge_sync_es_status` (`sync_es_status`),
  KEY `idx_ops_knowledge_source_alert_id` (`source_alert_id`),
  KEY `idx_ops_knowledge_source_ticket_id` (`source_ticket_id`),
  CONSTRAINT `fk_ops_knowledge_source_alert_id` FOREIGN KEY (`source_alert_id`) REFERENCES `ops_alert` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_ops_knowledge_source_ticket_id` FOREIGN KEY (`source_ticket_id`) REFERENCES `ops_ticket` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';
SET FOREIGN_KEY_CHECKS = 1;