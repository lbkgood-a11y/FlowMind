package com.triobase.service.ops.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.notification.entity.NotificationOutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationOutboxMapper extends BaseMapper<NotificationOutboxEntity> {

    @Select("""
            SELECT * FROM ops_notification_outbox
            WHERE published_at IS NULL
              AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now})
              AND (locked_at IS NULL OR locked_at &lt; #{staleBefore})
            ORDER BY created_at, id
            LIMIT #{limit}
            """)
    List<NotificationOutboxEntity> findReady(@Param("now") LocalDateTime now,
                                             @Param("staleBefore") LocalDateTime staleBefore,
                                             @Param("limit") int limit);

    @Update("""
            UPDATE ops_notification_outbox
            SET locked_at = #{now}, locked_by = #{worker}, attempt_count = attempt_count + 1
            WHERE id = #{id} AND published_at IS NULL
              AND (locked_at IS NULL OR locked_at &lt; #{staleBefore})
            """)
    int claim(@Param("id") String id,
              @Param("worker") String worker,
              @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore);

    @Update("""
            UPDATE ops_notification_outbox
            SET published_at = #{publishedAt}, locked_at = NULL, locked_by = NULL, next_attempt_at = NULL
            WHERE id = #{id} AND locked_by = #{worker} AND published_at IS NULL
            """)
    int acknowledge(@Param("id") String id,
                    @Param("worker") String worker,
                    @Param("publishedAt") LocalDateTime publishedAt);

    @Update("""
            UPDATE ops_notification_outbox
            SET locked_at = NULL, locked_by = NULL, next_attempt_at = #{nextAttemptAt}
            WHERE id = #{id} AND locked_by = #{worker} AND published_at IS NULL
            """)
    int releaseForRetry(@Param("id") String id,
                        @Param("worker") String worker,
                        @Param("nextAttemptAt") LocalDateTime nextAttemptAt);
}
