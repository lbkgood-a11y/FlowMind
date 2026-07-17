package com.triobase.service.openapi.infrastructure.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.openapi.domain.entity.PolicyEnforcementState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
@Mapper public interface PolicyEnforcementStateMapper extends BaseMapper<PolicyEnforcementState> {
 @Update("""
 INSERT INTO oa_policy_enforcement_state(enforcement_point,tenant_id,environment,required_policy_version,applied_policy_version,last_reported_at,drift_state)
 VALUES(#{point},#{tenant},#{environment},#{required},0,CURRENT_TIMESTAMP,'LAGGING')
 ON CONFLICT(enforcement_point,environment,tenant_id) DO UPDATE SET required_policy_version=EXCLUDED.required_policy_version,
 drift_state=CASE WHEN oa_policy_enforcement_state.applied_policy_version>=EXCLUDED.required_policy_version THEN 'CURRENT' ELSE 'LAGGING' END
 """) int requireVersion(@Param("point")String point,@Param("tenant")String tenant,@Param("environment")String environment,@Param("required")Long required);
 @Update("""
 UPDATE oa_policy_enforcement_state SET applied_policy_version=#{applied},last_reported_at=CURRENT_TIMESTAMP,
 drift_state=CASE WHEN #{applied}>=required_policy_version THEN 'CURRENT' ELSE 'LAGGING' END
 WHERE enforcement_point=#{point} AND tenant_id=#{tenant} AND environment=#{environment}
 """) int reportApplied(@Param("point")String point,@Param("tenant")String tenant,@Param("environment")String environment,@Param("applied")Long applied);
 @Select("SELECT enforcement_point,tenant_id,environment,required_policy_version,applied_policy_version,last_reported_at,drift_state FROM oa_policy_enforcement_state WHERE enforcement_point=#{point} AND tenant_id=#{tenant} AND environment=#{environment}") PolicyEnforcementState find(@Param("point")String point,@Param("tenant")String tenant,@Param("environment")String environment);
}
