package com.triobase.service.openapi.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StructureVersionMapper extends BaseMapper<StructureVersion> {
}
