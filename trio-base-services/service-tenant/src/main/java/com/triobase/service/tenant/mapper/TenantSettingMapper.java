package com.triobase.service.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.tenant.entity.SysTenantSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantSettingMapper extends BaseMapper<SysTenantSetting> {
}
