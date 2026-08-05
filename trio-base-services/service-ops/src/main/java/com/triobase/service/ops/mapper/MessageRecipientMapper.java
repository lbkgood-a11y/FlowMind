package com.triobase.service.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.ops.entity.OpsMessageRecipient;
import com.triobase.service.ops.dto.MessageCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
/**
 * 旧版站内消息收件人事实的数据访问契约。
 *
 * <p>所有查询必须显式携带租户条件；迁移期双写不能借助该 Mapper 跨租户聚合或改写
 * 新版收件投影。</p>
 */
public interface MessageRecipientMapper extends BaseMapper<OpsMessageRecipient> {

    /**
     * 一次返回当前管理页所需统计，避免按消息逐条 count。
     * tenantId 不能省略，即使 messageId 全局唯一；这是运行时租户隔离的纵深防线。
     */
    @Select("""
            <script>
            SELECT message_id AS messageId,
                   count(*) AS recipientCount,
                   sum(CASE WHEN read_status = 1 THEN 1 ELSE 0 END) AS readCount
            FROM ops_message_recipient
            WHERE tenant_id = #{tenantId}
              AND message_id IN
              <foreach collection='messageIds' item='id' open='(' separator=',' close=')'>
                #{id}
              </foreach>
            GROUP BY message_id
            </script>
            """)
    List<MessageCountRow> countByMessageIds(@Param("tenantId") String tenantId,
                                            @Param("messageIds") List<String> messageIds);
}
