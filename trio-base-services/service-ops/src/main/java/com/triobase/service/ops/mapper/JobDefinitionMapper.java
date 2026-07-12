package com.triobase.service.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.entity.OpsJobDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobDefinitionMapper extends BaseMapper<OpsJobDefinition> {
}
