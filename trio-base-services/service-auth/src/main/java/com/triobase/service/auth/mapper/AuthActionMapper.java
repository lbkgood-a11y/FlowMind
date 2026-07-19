package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysAuthAction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthActionMapper extends BaseMapper<SysAuthAction> {
}
