package com.triobase.common.core.entity;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.triobase.common.core.context.SecurityContextHolder;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String userId = resolveUserId();

        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createdBy", String.class, userId);
        this.setFieldValByName("updatedAt", now, metaObject);
        this.setFieldValByName("updatedBy", userId, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
        this.setFieldValByName("updatedBy", resolveUserId(), metaObject);
    }

    private static String resolveUserId() {
        String userId = SecurityContextHolder.getUserId();
        return userId != null ? userId : "SYSTEM";
    }
}
