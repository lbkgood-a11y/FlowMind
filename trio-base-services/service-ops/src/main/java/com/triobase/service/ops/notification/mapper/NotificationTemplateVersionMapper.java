package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationTemplateVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationTemplateVersionMapper extends BaseMapper<NotificationTemplateVersionEntity> {
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM ops_notification_template_version WHERE tenant_id = #{tenantId} AND template_id = #{templateId}")
    int maxVersion(@Param("tenantId") String tenantId, @Param("templateId") String templateId);

    @Select("SELECT * FROM ops_notification_template_version WHERE tenant_id = #{tenantId} AND id = #{id}")
    NotificationTemplateVersionEntity findOwned(@Param("tenantId") String tenantId, @Param("id") String id);

    @Select("SELECT * FROM ops_notification_template_version WHERE tenant_id = #{tenantId} AND template_id = #{templateId} ORDER BY version_no DESC")
    List<NotificationTemplateVersionEntity> findVersions(@Param("tenantId") String tenantId,
                                                          @Param("templateId") String templateId);

    @Update("""
            UPDATE ops_notification_template_version SET template_state = #{toState}
            WHERE tenant_id = #{tenantId} AND id = #{id} AND template_state = #{fromState}
            """)
    int transition(@Param("tenantId") String tenantId, @Param("id") String id,
                   @Param("fromState") String fromState, @Param("toState") String toState);
}

