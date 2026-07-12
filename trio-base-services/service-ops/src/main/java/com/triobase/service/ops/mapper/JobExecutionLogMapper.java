package com.triobase.service.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.entity.OpsJobExecutionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobExecutionLogMapper extends BaseMapper<OpsJobExecutionLog> {
}
