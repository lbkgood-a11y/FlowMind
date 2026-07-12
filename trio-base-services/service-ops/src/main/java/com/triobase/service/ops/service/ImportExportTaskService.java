package com.triobase.service.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.ops.dto.CreateImportExportTaskRequest;
import com.triobase.service.ops.dto.UpdateTaskProgressRequest;
import com.triobase.service.ops.entity.OpsImportExportTask;
import com.triobase.service.ops.mapper.ImportExportTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ImportExportTaskService {

    private static final String IMPORT = "IMPORT";
    private static final String EXPORT = "EXPORT";
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String CANCELLED = "CANCELLED";

    private final ImportExportTaskMapper taskMapper;
    private final RequestContextService contextService;

    public PageResult<OpsImportExportTask> page(int page,
                                                int size,
                                                String taskType,
                                                String businessType,
                                                String status,
                                                String createdBy) {
        LambdaQueryWrapper<OpsImportExportTask> wrapper = new LambdaQueryWrapper<OpsImportExportTask>()
                .eq(OpsImportExportTask::getTenantId, contextService.tenantId())
                .eq(StringUtils.hasText(taskType), OpsImportExportTask::getTaskType, taskType)
                .eq(StringUtils.hasText(businessType), OpsImportExportTask::getBusinessType, businessType)
                .eq(StringUtils.hasText(status), OpsImportExportTask::getStatus, status)
                .eq(StringUtils.hasText(createdBy), OpsImportExportTask::getCreatedBy, createdBy)
                .orderByDesc(OpsImportExportTask::getCreatedAt);
        IPage<OpsImportExportTask> result = taskMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public PageResult<OpsImportExportTask> mine(int page, int size, String status) {
        return page(page, size, null, null, status, contextService.userId());
    }

    public OpsImportExportTask detail(String id) {
        return requireTask(id);
    }

    @Transactional
    public OpsImportExportTask createImport(CreateImportExportTaskRequest request) {
        return createTask(IMPORT, request);
    }

    @Transactional
    public OpsImportExportTask createExport(CreateImportExportTaskRequest request) {
        return createTask(EXPORT, request);
    }

    @Transactional
    public OpsImportExportTask cancel(String id) {
        OpsImportExportTask task = requireTask(id);
        if (!PENDING.equals(task.getStatus()) && !RUNNING.equals(task.getStatus())) {
            throw new BizException(45302, "TASK_CANNOT_CANCEL");
        }
        task.setStatus(CANCELLED);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    @Transactional
    public OpsImportExportTask updateProgress(String id, UpdateTaskProgressRequest request) {
        OpsImportExportTask task = requireTask(id);
        if (StringUtils.hasText(request.getStatus())) {
            task.setStatus(request.getStatus());
        }
        if (request.getProgress() != null) {
            task.setProgress(Math.max(0, Math.min(100, request.getProgress())));
        }
        task.setResultFileId(request.getResultFileId());
        task.setFailureFileId(request.getFailureFileId());
        task.setSuccessCount(request.getSuccessCount() != null ? request.getSuccessCount() : task.getSuccessCount());
        task.setFailureCount(request.getFailureCount() != null ? request.getFailureCount() : task.getFailureCount());
        task.setFailureReason(request.getFailureReason());
        if (RUNNING.equals(task.getStatus()) && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if (SUCCESS.equals(task.getStatus()) || FAILED.equals(task.getStatus()) || CANCELLED.equals(task.getStatus())) {
            task.setFinishedAt(LocalDateTime.now());
            if (SUCCESS.equals(task.getStatus())) {
                task.setProgress(100);
            }
        }
        taskMapper.updateById(task);
        return task;
    }

    public OpsImportExportTask result(String id) {
        return requireTask(id);
    }

    private OpsImportExportTask createTask(String taskType, CreateImportExportTaskRequest request) {
        OpsImportExportTask task = new OpsImportExportTask();
        task.setId(UlidGenerator.nextUlid());
        task.setTenantId(contextService.tenantId());
        task.setTaskType(taskType);
        task.setBusinessType(request.getBusinessType());
        task.setTaskName(request.getTaskName());
        task.setRequestParams(request.getRequestParams());
        task.setStatus(PENDING);
        task.setProgress(0);
        task.setSuccessCount(0);
        task.setFailureCount(0);
        taskMapper.insert(task);
        return task;
    }

    private OpsImportExportTask requireTask(String id) {
        OpsImportExportTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(45301, "IMPORT_EXPORT_TASK_NOT_FOUND");
        }
        return task;
    }
}
