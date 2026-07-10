package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
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
            "WHERE ur.user_id = #{userId} AND r.status = 1" +
            ") permissions WHERE code IS NOT NULL AND code <> ''")
    List<String> selectPermissionsByUserId(String userId);

    @Select("SELECT r.role_code FROM sys_role r " +
            "JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(String userId);
}
