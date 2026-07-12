package com.triobase.service.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.entity.OpsFileReference;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileReferenceMapper extends BaseMapper<OpsFileReference> {
}
