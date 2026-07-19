package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysAuthField;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthFieldMapper extends BaseMapper<SysAuthField> {
}
