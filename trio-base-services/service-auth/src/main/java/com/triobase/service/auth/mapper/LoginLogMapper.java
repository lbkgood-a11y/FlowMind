package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapper<SysLoginLog> {
}
