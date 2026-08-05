package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationChannelEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationChannelMapper extends BaseMapper<NotificationChannelEntity> {

    @Insert("""
            INSERT INTO ops_notification_channel
                (id, tenant_id, channel_code, capability_state, desired_enabled, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{channelCode}, #{state}, #{enabled}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (tenant_id, channel_code) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") String id,
                       @Param("tenantId") String tenantId,
                       @Param("channelCode") String channelCode,
                       @Param("state") String state,
                       @Param("enabled") int enabled);

    @Select("""
            SELECT * FROM ops_notification_channel
            WHERE tenant_id = #{tenantId}
            ORDER BY CASE channel_code
                WHEN 'IN_APP' THEN 1 WHEN 'EMAIL' THEN 2 WHEN 'SMS' THEN 3
                WHEN 'WE_COM' THEN 4 WHEN 'DINGTALK' THEN 5 ELSE 99 END
            """)
    List<NotificationChannelEntity> findByTenant(@Param("tenantId") String tenantId);

    @Select("SELECT * FROM ops_notification_channel WHERE tenant_id = #{tenantId} AND channel_code = #{channelCode}")
    NotificationChannelEntity findOwned(@Param("tenantId") String tenantId,
                                         @Param("channelCode") String channelCode);

    @Update("""
            UPDATE ops_notification_channel
            SET capability_state = #{state}, adapter_key = #{adapterKey}, adapter_version = #{adapterVersion},
                validated_at = CURRENT_TIMESTAMP, validation_summary = #{summary},
                desired_enabled = CASE WHEN #{state} = 'READY' THEN desired_enabled ELSE 0 END,
                updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND channel_code = #{channelCode}
            """)
    int updateValidation(@Param("tenantId") String tenantId,
                         @Param("channelCode") String channelCode,
                         @Param("state") String state,
                         @Param("adapterKey") String adapterKey,
                         @Param("adapterVersion") String adapterVersion,
                         @Param("summary") String summary);

    @Update("""
            UPDATE ops_notification_channel SET desired_enabled = #{enabled},
                capability_state = CASE WHEN #{enabled} = 0 AND capability_state = 'READY'
                                        THEN 'DISABLED' ELSE capability_state END,
                updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND channel_code = #{channelCode}
              AND (#{enabled} = 0 OR capability_state = 'READY')
            """)
    int updateEnabledGuarded(@Param("tenantId") String tenantId,
                             @Param("channelCode") String channelCode,
                             @Param("enabled") int enabled);
}
