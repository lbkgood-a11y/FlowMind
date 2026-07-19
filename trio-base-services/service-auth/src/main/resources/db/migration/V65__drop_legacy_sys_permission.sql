-- sys_auth_resource/action/grant is now the only function authorization model.
-- sys_permission was kept only to migrate legacy permission_id values and is no
-- longer referenced by runtime code or database projections after V64.

DROP TABLE IF EXISTS sys_permission;
