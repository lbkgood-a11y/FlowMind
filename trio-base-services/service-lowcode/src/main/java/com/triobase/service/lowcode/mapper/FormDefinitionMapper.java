package com.triobase.service.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FormDefinitionMapper extends BaseMapper<LcFormDefinition> {
}
