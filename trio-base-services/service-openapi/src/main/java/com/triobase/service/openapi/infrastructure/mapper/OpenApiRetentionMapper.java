package com.triobase.service.openapi.infrastructure.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpenApiRetentionMapper {

    @Delete("DELETE FROM oa_execution_diagnostic WHERE expires_at < CURRENT_TIMESTAMP")
    int deleteExpiredDiagnostics();

    @Delete("DELETE FROM oa_callback_nonce WHERE expires_at < CURRENT_TIMESTAMP")
    int deleteExpiredCallbackNonces();

    @Delete("DELETE FROM oa_callback_inbox WHERE retention_until < CURRENT_TIMESTAMP")
    int deleteExpiredCallbacks();

    @Delete("""
            DELETE FROM oa_idempotency_record
            WHERE expires_at < CURRENT_TIMESTAMP
               OR execution_id IN (
                   SELECT id FROM oa_execution WHERE retention_until < CURRENT_TIMESTAMP)
            """)
    int deleteExpiredIdempotencyRecords();

    @Delete("DELETE FROM oa_execution WHERE retention_until < CURRENT_TIMESTAMP")
    int deleteExpiredExecutions();
}
