USE ops_ai_platform;

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM ops_ticket_log;
DELETE FROM ai_diagnosis;
DELETE FROM ops_knowledge;
DELETE FROM ops_ticket;
DELETE FROM ops_alert;

SET FOREIGN_KEY_CHECKS = 1;
