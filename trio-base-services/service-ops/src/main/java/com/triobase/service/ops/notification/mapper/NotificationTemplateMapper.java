package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationTemplateMapper extends BaseMapper<NotificationTemplateEntity> {
    @Select("""
            SELECT * FROM ops_notification_template
            WHERE tenant_id = #{tenantId} AND template_key = #{templateKey}
              AND channel_code = #{channelCode} AND locale_code = #{localeCode}
            """)
    NotificationTemplateEntity findOwned(@Param("tenantId") String tenantId,
                                           @Param("templateKey") String templateKey,
                                           @Param("channelCode") String channelCode,
                                           @Param("localeCode") String localeCode);

    @Update("""
            UPDATE ops_notification_template SET current_version_id = #{versionId}, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{templateId}
            """)
    int setCurrentVersion(@Param("tenantId") String tenantId,
                          @Param("templateId") String templateId,
                          @Param("versionId") String versionId);
}

