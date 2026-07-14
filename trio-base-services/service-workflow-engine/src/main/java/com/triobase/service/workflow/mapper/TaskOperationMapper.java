package com.triobase.service.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.workflow.entity.TaskOperation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskOperationMapper extends BaseMapper<TaskOperation> {

    @Insert("""
            INSERT INTO wf_task_operation (
                id, operation_id, process_instance_id, source_task_id, target_task_id,
                action, operator_id, operator_name, target_user_id, target_user_name,
                target_node_id, comment, status, trace_id, result_json,
                created_at, updated_at
            ) VALUES (
                #{id}, #{operationId}, #{processInstanceId}, #{sourceTaskId}, #{targetTaskId},
                #{action}, #{operatorId}, #{operatorName}, #{targetUserId}, #{targetUserName},
                #{targetNodeId}, #{comment}, #{status}, #{traceId}, #{resultJson},
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (operation_id) DO NOTHING
            """)
    int insertIfAbsent(TaskOperation operation);
}
