package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationRoutingPolicyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationRoutingPolicyMapper extends BaseMapper<NotificationRoutingPolicyEntity> {
    @Select("""
            SELECT * FROM ops_notification_routing_policy
            WHERE tenant_id = #{tenantId} AND category_code = #{categoryCode}
              AND priority_code IN (#{priorityCode}, 'NORMAL') AND enabled = 1
            ORDER BY CASE WHEN priority_code = #{priorityCode} THEN 0 ELSE 1 END LIMIT 1
            """)
    NotificationRoutingPolicyEntity findEffective(@Param("tenantId") String tenantId,
                                                    @Param("categoryCode") String categoryCode,
                                                    @Param("priorityCode") String priorityCode);

    @Select("""
            SELECT * FROM ops_notification_routing_policy
            WHERE tenant_id = #{tenantId} AND category_code = #{categoryCode} AND priority_code = #{priorityCode}
            """)
    NotificationRoutingPolicyEntity findOwned(@Param("tenantId") String tenantId,
                                                @Param("categoryCode") String categoryCode,
                                                @Param("priorityCode") String priorityCode);

    @Update("""
            UPDATE ops_notification_routing_policy
            SET ordered_channels = #{channels}, fallback_enabled = #{fallback}, quiet_hours_json = #{quietHours},
                mandatory_category = #{mandatory}, enabled = 1, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND category_code = #{categoryCode} AND priority_code = #{priorityCode}
            """)
    int updateOwned(@Param("tenantId") String tenantId,
                    @Param("categoryCode") String categoryCode,
                    @Param("priorityCode") String priorityCode,
                    @Param("channels") String channels,
                    @Param("fallback") int fallback,
                    @Param("quietHours") String quietHours,
                    @Param("mandatory") int mandatory);
}

