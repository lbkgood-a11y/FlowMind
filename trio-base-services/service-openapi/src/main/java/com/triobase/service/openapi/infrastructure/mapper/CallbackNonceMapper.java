package com.triobase.service.openapi.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.common.openapi.entity.CallbackNonce;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CallbackNonceMapper extends BaseMapper<CallbackNonce> {
}
