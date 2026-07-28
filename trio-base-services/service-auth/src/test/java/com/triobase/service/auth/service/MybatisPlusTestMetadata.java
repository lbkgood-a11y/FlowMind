package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthDecisionLog;
import com.triobase.service.auth.entity.SysAuthField;
import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthGuardTemplate;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.entity.SysDataPolicy;
import com.triobase.service.auth.entity.SysDataPolicyDimension;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.entity.SysUserRole;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

final class MybatisPlusTestMetadata {

    private MybatisPlusTestMetadata() {
    }

    static void initialize() {
        for (Class<?> entityType : new Class<?>[]{
                SysAuthAction.class, SysAuthDecisionLog.class, SysAuthField.class,
                SysAuthFieldPolicy.class, SysAuthGrant.class, SysAuthGuardTemplate.class,
                SysAuthResource.class, SysDataPolicy.class, SysDataPolicyDimension.class,
                SysRole.class, SysUser.class, SysUserRole.class}) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
            assistant.setCurrentNamespace(entityType.getName());
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
