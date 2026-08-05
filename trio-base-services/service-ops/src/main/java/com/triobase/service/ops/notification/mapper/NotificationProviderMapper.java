package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationProviderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationProviderMapper extends BaseMapper<NotificationProviderEntity> {

    @Select("SELECT * FROM ops_notification_provider WHERE tenant_id = #{tenantId} ORDER BY channel_code, provider_key")
    List<NotificationProviderEntity> findByTenant(@Param("tenantId") String tenantId);

    @Select("""
            SELECT * FROM ops_notification_provider
            WHERE tenant_id = #{tenantId} AND channel_code = #{channelCode} AND provider_key = #{providerKey}
            """)
    NotificationProviderEntity findOwned(@Param("tenantId") String tenantId,
                                           @Param("channelCode") String channelCode,
                                           @Param("providerKey") String providerKey);

    @Update("""
            UPDATE ops_notification_provider
            SET display_name = #{displayName}, credential_reference = #{credentialReference},
                settings_json = #{settingsJson}, enabled = 0, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND channel_code = #{channelCode} AND provider_key = #{providerKey}
            """)
    int updateOwned(@Param("tenantId") String tenantId,
                    @Param("channelCode") String channelCode,
                    @Param("providerKey") String providerKey,
                    @Param("displayName") String displayName,
                    @Param("credentialReference") String credentialReference,
                    @Param("settingsJson") String settingsJson);
}

