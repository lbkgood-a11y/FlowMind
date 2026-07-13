package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.ProcessInstanceResponse;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.workflow.ProcessWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceService {

    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessPackageService processPackageService;
    private final WorkflowClient workflowClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProcessInstanceResponse startProcess(StartProcessRequest request) {
        // 1. 获取已发布的流程包
        ProcessPackage pkg = processPackageService.findPublishedByKey(request.getProcessKey());
        ProcessPackageDefinition packageDef;
        try {
            packageDef = objectMapper.readValue(pkg.getProcessJson(), ProcessPackageDefinition.class);
        } catch (JsonProcessingException e) {
            throw new BizException(50000, "INVALID_PACKAGE_JSON");
        }

        // 2. 获取当前用户
        String userId = SecurityContextHolder.getUserId();
        String userName = SecurityContextHolder.getUsername();
        if (userId == null) {
            throw new BizException(40100, "UNAUTHENTICATED");
        }

        // 3. 构建表单数据 JSON
        String formDataJson = null;
        try {
            if (request.getFormData() != null) {
                formDataJson = objectMapper.writeValueAsString(request.getFormData());
            }
        } catch (Exception e) {
            throw new BizException(40000, "INVALID_FORM_DATA");
        }

        // 4. 创建流程实例（先入库，保证业务记录先存在）
        String instanceId = UlidGenerator.nextUlid();
        ProcessInstance instance = new ProcessInstance();
        instance.setId(instanceId);
        instance.setProcessPackageId(pkg.getId());
        instance.setProcessKey(pkg.getProcessKey());
        instance.setProcessName(pkg.getName());
        instance.setVersion(pkg.getVersion());
        instance.setTitle(request.getTitle() != null ? request.getTitle() : userName + "的" + pkg.getName());
        instance.setStatus("RUNNING");
        instance.setFormData(formDataJson);
        instance.setInitiatorId(userId);
        instance.setInitiatorName(userName);
        instance.setStartedAt(LocalDateTime.now());
        processInstanceMapper.insert(instance);

        // 5. 启动 Temporal Workflow
        String workflowId = "process-" + instanceId;
        ProcessWorkflow workflow = workflowClient.newWorkflowStub(
                ProcessWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue("service-workflow-engine")
                        .build());

        // Temporal 的 WorkflowClient.start 是异步调用，不会阻塞
        WorkflowClient.start(workflow::startProcess,
                packageDef, instanceId, userId, userName, formDataJson);

        // 6. 回填 Workflow ID
        instance.setWorkflowId(workflowId);
        processInstanceMapper.updateById(instance);

        log.info("Process started: instanceId={}, workflowId={}, processKey={}",
                instanceId, workflowId, request.getProcessKey());

        return toResponse(instance);
    }

    public PageResult<ProcessInstanceResponse> list(int pageNo, int pageSize, String status) {
        LambdaQueryWrapper<ProcessInstance> qw = new LambdaQueryWrapper<ProcessInstance>()
                .orderByDesc(ProcessInstance::getStartedAt);
        if (status != null) {
            qw.eq(ProcessInstance::getStatus, status);
        }
        IPage<ProcessInstance> page = processInstanceMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), pageNo, pageSize);
    }

    public ProcessInstanceResponse getById(String id) {
        ProcessInstance instance = processInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException(40400, "PROCESS_INSTANCE_NOT_FOUND");
        }
        return toResponse(instance);
    }

    private ProcessInstanceResponse toResponse(ProcessInstance instance) {
        ProcessInstanceResponse resp = new ProcessInstanceResponse();
        resp.setId(instance.getId());
        resp.setProcessPackageId(instance.getProcessPackageId());
        resp.setProcessKey(instance.getProcessKey());
        resp.setProcessName(instance.getProcessName());
        resp.setVersion(instance.getVersion());
        resp.setTitle(instance.getTitle());
        resp.setStatus(instance.getStatus());
        resp.setFormData(instance.getFormData());
        resp.setInitiatorId(instance.getInitiatorId());
        resp.setInitiatorName(instance.getInitiatorName());
        resp.setCurrentNodeId(instance.getCurrentNodeId());
        resp.setStartedAt(instance.getStartedAt());
        resp.setCompletedAt(instance.getCompletedAt());
        resp.setCreatedAt(instance.getCreatedAt());
        return resp;
    }
}
