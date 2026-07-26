package com.triobase.service.apiruntime.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.common.openapi.entity.MappingVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MappingVersionMapper extends BaseMapper<MappingVersion> {
}
