-- 恢复 admin 账号的 ADMIN 角色（在 MySQL 136 上执行）
-- mysql -h 192.168.88.136 -u root1 -p ops_ai_platform < restore-admin-role.sql

USE ops_ai_platform;

-- 确保 ADMIN 角色存在
INSERT INTO sys_role(role_name, role_code, description)
VALUES ('Administrator', 'ADMIN', 'System administrator')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- 移除 admin 上错误的角色，重新绑定 ADMIN
DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
WHERE u.username = 'admin';

INSERT INTO sys_user_role(user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ADMIN'
WHERE u.username = 'admin';

-- 验证
SELECT u.id, u.username, r.role_code, r.role_name
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id = u.id
LEFT JOIN sys_role r ON r.id = ur.role_id
WHERE u.username = 'admin';
