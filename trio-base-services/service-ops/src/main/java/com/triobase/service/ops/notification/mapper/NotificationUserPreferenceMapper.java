package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationUserPreferenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationUserPreferenceMapper extends BaseMapper<NotificationUserPreferenceEntity> {
    @Select("""
            SELECT * FROM ops_notification_user_preference
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
            ORDER BY category_code, channel_code
            """)
    List<NotificationUserPreferenceEntity> findByUser(@Param("tenantId") String tenantId,
                                                       @Param("userId") String userId);

    @Select("""
            SELECT * FROM ops_notification_user_preference
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
              AND category_code = #{categoryCode} AND channel_code = #{channelCode}
            """)
    NotificationUserPreferenceEntity findOwned(@Param("tenantId") String tenantId,
                                                 @Param("userId") String userId,
                                                 @Param("categoryCode") String categoryCode,
                                                 @Param("channelCode") String channelCode);

    @Update("""
            UPDATE ops_notification_user_preference SET enabled = #{enabled}, quiet_hours_json = #{quietHours},
                updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
              AND category_code = #{categoryCode} AND channel_code = #{channelCode}
            """)
    int updateOwned(@Param("tenantId") String tenantId, @Param("userId") String userId,
                    @Param("categoryCode") String categoryCode, @Param("channelCode") String channelCode,
                    @Param("enabled") int enabled, @Param("quietHours") String quietHours);
}

