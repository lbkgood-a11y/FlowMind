package com.triobase.service.openapi.infrastructure.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.openapi.domain.entity.ApiProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.util.List;
@Mapper public interface ApiProductMapper extends BaseMapper<ApiProduct> {
 @InterceptorIgnore(tenantLine="true")
 @Select("""
 SELECT p.* FROM oa_api_product p
 WHERE p.lifecycle_state='ACTIVE' AND (
   p.visibility='PLATFORM_PUBLIC' OR
   (p.visibility='TENANT' AND p.tenant_id=#{tenantId}) OR
   (p.visibility='PRIVATE' AND EXISTS(SELECT 1 FROM oa_api_product_access_grant g
      WHERE g.api_product_id=p.id AND g.grantee_type='APPLICATION' AND g.grantee_id=#{applicationId})))
 ORDER BY p.product_key
 """) List<ApiProduct> discover(@Param("tenantId")String tenantId,@Param("applicationId")String applicationId);
}
