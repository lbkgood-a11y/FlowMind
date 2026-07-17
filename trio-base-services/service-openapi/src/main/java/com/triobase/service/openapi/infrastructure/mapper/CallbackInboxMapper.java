package com.triobase.service.openapi.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CallbackInboxMapper extends BaseMapper<CallbackInbox> {

    @Select("""
            SELECT * FROM oa_callback_inbox
            WHERE inbox_state = 'SIGNAL_PENDING'
              AND (next_signal_at IS NULL OR next_signal_at <= CURRENT_TIMESTAMP)
            ORDER BY received_at
            LIMIT #{limit}
            """)
    List<CallbackInbox> findSignalPending(int limit);

    @Update("""
            UPDATE oa_callback_inbox SET inbox_state = 'SIGNALING', updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND inbox_state = 'SIGNAL_PENDING'
            """)
    int claimForSignal(String id);

    @Update("""
            UPDATE oa_callback_inbox SET inbox_state = 'SIGNAL_PENDING',
                next_signal_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE inbox_state = 'SIGNALING'
              AND updated_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
            """)
    int resetStaleSignalClaims();
}
