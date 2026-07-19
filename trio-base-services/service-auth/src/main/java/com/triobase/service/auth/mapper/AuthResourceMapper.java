package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysAuthResource;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthResourceMapper extends BaseMapper<SysAuthResource> {
}
