package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.triobase.service.lowcode.entity.LcApplication;
import com.triobase.service.lowcode.entity.LcApplicationAction;
import com.triobase.service.lowcode.entity.LcApplicationPage;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormFieldDefinition;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.entity.LcFormInstanceRelation;
import com.triobase.service.lowcode.entity.LcFormInstanceWorkflowAudit;
import com.triobase.service.lowcode.entity.LcFormRelation;
import com.triobase.service.lowcode.entity.LowcodeActionAuditEvent;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

final class MybatisPlusTestMetadata {

    private MybatisPlusTestMetadata() {
    }

    static void initialize() {
        for (Class<?> entityType : new Class<?>[]{
                LcApplication.class, LcApplicationAction.class, LcApplicationPage.class,
                LcApplicationVersion.class, LcFormDefinition.class, LcFormFieldDefinition.class,
                LcFormInstance.class, LcFormInstanceRelation.class, LcFormInstanceWorkflowAudit.class,
                LcFormRelation.class, LowcodeActionAuditEvent.class}) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
            assistant.setCurrentNamespace(entityType.getName());
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
