package com.triobase.service.openapi.infrastructure.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.openapi.domain.entity.PolicySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper public interface PolicySnapshotMapper extends BaseMapper<PolicySnapshot>{
 @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{seriesKey},0))) locked") Integer lockSeries(@Param("seriesKey")String seriesKey);
 @Select("SELECT COALESCE(MAX(snapshot_version),0)+1 FROM oa_policy_snapshot WHERE environment=#{environment} AND ((#{tenantId} IS NULL AND tenant_id IS NULL) OR tenant_id=#{tenantId})") Long nextVersion(@Param("tenantId")String tenantId,@Param("environment")String environment);
}
