package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMenuMapper extends BaseMapper<SysRoleMenu> {

    @Select("SELECT rm.menu_id FROM sys_role_menu rm WHERE rm.role_id = #{roleId}")
    List<String> selectMenuIdsByRoleId(String roleId);

    @Select("SELECT DISTINCT rm.menu_id FROM sys_role_menu rm " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectMenuIdsByUserId(String userId);
}
