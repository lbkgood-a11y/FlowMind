package com.triobase.service.openapi.infrastructure.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.openapi.domain.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> { }
