package com.triobase.service.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrgScopeMapper {

    @Select("""
            SELECT id
            FROM sys_org_dimension
            WHERE tenant_id = #{tenantId}
              AND dimension_code = #{dimensionCode}
              AND status = 1
            LIMIT 1
            """)
    String selectDimensionId(@Param("tenantId") String tenantId,
                             @Param("dimensionCode") String dimensionCode);

    @Select("""
            SELECT org_unit_id
            FROM sys_user_org_unit
            WHERE tenant_id = #{tenantId}
              AND dimension_id = #{dimensionId}
              AND user_id = #{userId}
              AND status = 1
              AND (effective_from IS NULL OR effective_from <= CURRENT_DATE)
              AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
            ORDER BY is_primary DESC, created_at ASC, org_unit_id ASC
            """)
    List<String> selectActiveUserOrgUnitIds(@Param("tenantId") String tenantId,
                                            @Param("dimensionId") String dimensionId,
                                            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT DISTINCT child.child_unit_id
            FROM sys_org_relation root
            JOIN sys_org_relation child
              ON child.tenant_id = root.tenant_id
             AND child.dimension_id = root.dimension_id
             AND child.status = 1
             AND (
                  child.tree_path = root.tree_path
                  OR child.tree_path LIKE root.tree_path || '/%'
             )
            WHERE root.tenant_id = #{tenantId}
              AND root.dimension_id = #{dimensionId}
              AND root.status = 1
              AND root.child_unit_id IN
              <foreach collection="rootOrgUnitIds" item="orgUnitId" open="(" separator="," close=")">
                #{orgUnitId}
              </foreach>
            ORDER BY child.child_unit_id
            </script>
            """)
    List<String> selectOrgUnitAndDescendantIds(@Param("tenantId") String tenantId,
                                               @Param("dimensionId") String dimensionId,
                                               @Param("rootOrgUnitIds") List<String> rootOrgUnitIds);
}
