package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysAuthDecisionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthDecisionLogMapper extends BaseMapper<SysAuthDecisionLog> {
}
