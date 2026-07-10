-- V10__cleanup_orphan_menus.sql
-- 删除非种子数据创建的运行时菜单（运维管理），保持数据库与迁移文件一致

DELETE FROM sys_menu WHERE id = '01KX5B4232TBT62C1T43Y9KBS7';
