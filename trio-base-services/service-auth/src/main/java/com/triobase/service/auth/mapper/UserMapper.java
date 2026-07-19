package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<SysUser> {

    @Select("SELECT DISTINCT code FROM (" +
            "SELECT COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action) AS code " +
            "FROM sys_role_menu rm " +
            "JOIN sys_menu m ON m.id = rm.menu_id " +
            "LEFT JOIN sys_permission p ON p.id = m.permission_id " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 " +
            "UNION " +
            "SELECT g.resource_code || ':' || g.action_code AS code " +
            "FROM sys_auth_grant g " +
            "JOIN sys_user_role ur ON ur.role_id = g.subject_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 " +
            "AND g.subject_type = 'ROLE' AND g.effect = 'ALLOW' AND g.status = 1 " +
            "UNION " +
            "SELECT g.resource_code || ':' || g.action_code AS code " +
            "FROM sys_auth_grant g " +
            "WHERE g.subject_type = 'USER' AND g.subject_id = #{userId} " +
            "AND g.effect = 'ALLOW' AND g.status = 1" +
            ") permissions WHERE code IS NOT NULL AND code <> ''")
    List<String> selectPermissionsByUserId(String userId);

    @Select("SELECT r.role_code FROM sys_role r " +
            "JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(String userId);

    @Select("SELECT DISTINCT u.* FROM sys_user u " +
            "JOIN sys_user_role ur ON ur.user_id = u.id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE r.role_code = #{roleCode} AND r.status = 1 AND u.status = 1 " +
            "ORDER BY u.username")
    List<SysUser> selectEnabledUsersByRoleCode(@Param("roleCode") String roleCode);
}
