package com.triobase.service.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.tenant.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<SysTenant> {
}
