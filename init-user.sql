USE ops_ai_platform;

INSERT INTO sys_role(role_name, role_code, description)
VALUES ('Administrator', 'ADMIN', 'System administrator')
ON DUPLICATE KEY UPDATE
description = VALUES(description);

INSERT INTO sys_user(username, password, email, status)
VALUES ('admin', '123456', 'admin@example.com', 1)
ON DUPLICATE KEY UPDATE
password = VALUES(password),
status = VALUES(status);

INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ADMIN'
WHERE u.username = 'admin';
