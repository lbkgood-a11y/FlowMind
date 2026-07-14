package com.triobase.service.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.service.workflow.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    @Select("""
            SELECT task.*
            FROM wf_task task
            WHERE task.status = 'PENDING'
              AND (
                    task.assignee_id = #{userId}
                    OR (
                        task.assignee_id IS NULL
                        AND EXISTS (
                            SELECT 1
                            FROM wf_task_candidate candidate
                            WHERE candidate.task_id = task.id
                              AND candidate.user_id = #{userId}
                        )
                    )
                  )
            ORDER BY task.created_at DESC
            """)
    IPage<Task> selectPendingForUser(Page<Task> page, @Param("userId") String userId);

    @Update("""
            UPDATE wf_task task
            SET assignee_id = #{userId},
                assignee_name = #{username},
                claimed_at = COALESCE(task.claimed_at, CURRENT_TIMESTAMP),
                updated_at = CURRENT_TIMESTAMP
            WHERE task.id = #{taskId}
              AND task.status = 'PENDING'
              AND (
                    task.assignee_id = #{userId}
                    OR (
                        task.assignee_id IS NULL
                        AND EXISTS (
                            SELECT 1
                            FROM wf_task_candidate candidate
                            WHERE candidate.task_id = task.id
                              AND candidate.user_id = #{userId}
                        )
                    )
                  )
            """)
    int claimPendingTask(@Param("taskId") String taskId,
                         @Param("userId") String userId,
                         @Param("username") String username);
}
