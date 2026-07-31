package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysRoleAuthIntent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleAuthIntentMapper extends BaseMapper<SysRoleAuthIntent> {
}
