package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.workflow.client.LowcodeFormClient;
import com.triobase.service.workflow.dto.CreateProcessPackageRequest;
import com.triobase.service.workflow.dto.ProcessPackageResponse;
import com.triobase.service.workflow.dto.UpdateProcessPackageRequest;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProcessPackageService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_OFFLINE = "OFFLINE";

    private final ProcessPackageMapper processPackageMapper;
    private final ObjectMapper objectMapper;
    private final LowcodeFormClient lowcodeFormClient;
    private final ProcessDefinitionValidator processDefinitionValidator;
    private final FormSnapshotValidator formSnapshotValidator;

    public PageResult<ProcessPackageResponse> list(int pageNo, int pageSize) {
        IPage<ProcessPackage> page = processPackageMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<ProcessPackage>()
                        .orderByDesc(ProcessPackage::getUpdatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), pageNo, pageSize);
    }

    public ProcessPackageResponse getById(String id) {
        ProcessPackage pkg = processPackageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException(40400, "PROCESS_PACKAGE_NOT_FOUND");
        }
        return toResponse(pkg);
    }

    public ProcessPackageResponse getByKey(String processKey) {
        ProcessPackage pkg = processPackageMapper.selectOne(
                new LambdaQueryWrapper<ProcessPackage>()
                        .eq(ProcessPackage::getProcessKey, processKey)
                        .orderByDesc(ProcessPackage::getVersion)
                        .last("LIMIT 1"));
        if (pkg == null) {
            throw new BizException(40400, "PROCESS_PACKAGE_NOT_FOUND");
        }
        return toResponse(pkg);
    }

    @Transactional
    public ProcessPackageResponse create(CreateProcessPackageRequest request) {
        if (!StringUtils.hasText(request.getProcessKey())
                || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getProcessJson())) {
            throw new BizException(40000, "PROCESS_KEY_OR_NAME_REQUIRED");
        }

        String processKey = request.getProcessKey().trim();
        Long exists = processPackageMapper.selectCount(
                new LambdaQueryWrapper<ProcessPackage>()
                        .eq(ProcessPackage::getProcessKey, processKey));
        if (exists > 0) {
            throw new BizException(40000, "PROCESS_KEY_ALREADY_EXISTS");
        }

        FormSnapshot snapshot = extractInlineFormSnapshot(request.getProcessJson());

        ProcessPackage pkg = new ProcessPackage();
        pkg.setId(UlidGenerator.nextUlid());
        pkg.setProcessKey(processKey);
        pkg.setName(request.getName().trim());
        pkg.setCategory(normalizeCategory(request.getCategory()));
        pkg.setDescription(request.getDescription());
        pkg.setVersion(1);
        pkg.setStatus(STATUS_DRAFT);
        pkg.setProcessJson(request.getProcessJson());
        pkg.setFormSchema(snapshot.schema());
        pkg.setFormUiSchema(snapshot.uiSchema());
        pkg.setFormDefinitionId(normalizeBlank(request.getFormDefinitionId()));
        processPackageMapper.insert(pkg);
        return toResponse(pkg);
    }

    @Transactional
    public ProcessPackageResponse update(String id, UpdateProcessPackageRequest request) {
        ProcessPackage pkg = requirePackage(id);
        requireDraft(pkg);

        if (request.getName() != null) {
            if (!StringUtils.hasText(request.getName())) {
                throw new BizException(40000, "PROCESS_NAME_REQUIRED");
            }
            pkg.setName(request.getName().trim());
        }
        if (request.getCategory() != null) {
            pkg.setCategory(normalizeCategory(request.getCategory()));
        }
        if (request.getDescription() != null) {
            pkg.setDescription(request.getDescription());
        }
        if (request.getProcessJson() != null) {
            if (!StringUtils.hasText(request.getProcessJson())) {
                throw new BizException(40000, "PROCESS_JSON_REQUIRED");
            }
            FormSnapshot snapshot = extractInlineFormSnapshot(request.getProcessJson());
            pkg.setProcessJson(request.getProcessJson());
            pkg.setFormSchema(snapshot.schema());
            pkg.setFormUiSchema(snapshot.uiSchema());
        }
        if (request.getFormDefinitionId() != null) {
            pkg.setFormDefinitionId(normalizeBlank(request.getFormDefinitionId()));
            pkg.setFormDefinitionVersion(null);
        }

        processPackageMapper.updateById(pkg);
        return toResponse(pkg);
    }

    @Transactional
    public ProcessPackageResponse createNewVersion(String sourceId) {
        ProcessPackage source = requirePackage(sourceId);
        if (STATUS_DRAFT.equals(source.getStatus())) {
            throw new BizException(40000, "DRAFT_CANNOT_CREATE_NEW_VERSION");
        }
        ensureNoDraft(source.getProcessKey());

        ProcessPackage latest = processPackageMapper.selectOne(
                new LambdaQueryWrapper<ProcessPackage>()
                        .eq(ProcessPackage::getProcessKey, source.getProcessKey())
                        .orderByDesc(ProcessPackage::getVersion)
                        .last("LIMIT 1"));
        if (latest == null || !latest.getId().equals(source.getId())) {
            throw new BizException(40900, "ONLY_LATEST_VERSION_CAN_BE_DERIVED");
        }

        ProcessPackage draft = new ProcessPackage();
        draft.setId(UlidGenerator.nextUlid());
        draft.setProcessKey(source.getProcessKey());
        draft.setName(source.getName());
        draft.setCategory(source.getCategory());
        draft.setDescription(source.getDescription());
        draft.setVersion(source.getVersion() + 1);
        draft.setStatus(STATUS_DRAFT);
        draft.setProcessJson(source.getProcessJson());
        draft.setFormSchema(source.getFormSchema());
        draft.setFormUiSchema(source.getFormUiSchema());
        draft.setFormDefinitionId(source.getFormDefinitionId());
        draft.setFormDefinitionVersion(source.getFormDefinitionVersion());
        draft.setSourcePackageId(source.getId());
        processPackageMapper.insert(draft);
        return toResponse(draft);
    }

    @Transactional
    public ProcessPackageResponse publish(String id) {
        ProcessPackage pkg = requirePackage(id);
        requireDraft(pkg);
        processDefinitionValidator.validate(pkg.getProcessJson());

        FormSnapshot snapshot = StringUtils.hasText(pkg.getFormDefinitionId())
                ? loadPublishedFormSnapshot(pkg.getFormDefinitionId())
                : extractInlineFormSnapshot(pkg.getProcessJson());
        formSnapshotValidator.validate(snapshot.schema(), snapshot.uiSchema());
        pkg.setFormSchema(snapshot.schema());
        pkg.setFormUiSchema(snapshot.uiSchema());
        pkg.setFormDefinitionVersion(snapshot.version());
        pkg.setStatus(STATUS_PUBLISHED);
        pkg.setPublishedAt(LocalDateTime.now());
        processPackageMapper.updateById(pkg);
        return toResponse(pkg);
    }

    @Transactional
    public ProcessPackageResponse offline(String id) {
        ProcessPackage pkg = requirePackage(id);
        if (!STATUS_PUBLISHED.equals(pkg.getStatus())) {
            throw new BizException(40000, "ONLY_PUBLISHED_CAN_BE_OFFLINE");
        }
        pkg.setStatus(STATUS_OFFLINE);
        processPackageMapper.updateById(pkg);
        return toResponse(pkg);
    }

    @Transactional
    public void delete(String id) {
        ProcessPackage pkg = requirePackage(id);
        requireDraft(pkg);
        processPackageMapper.deleteById(id);
    }

    /**
     * 获取已发布的最新版本流程包
     */
    public ProcessPackage findPublishedByKey(String processKey) {
        ProcessPackage pkg = processPackageMapper.selectOne(
                new LambdaQueryWrapper<ProcessPackage>()
                        .eq(ProcessPackage::getProcessKey, processKey)
                        .eq(ProcessPackage::getStatus, STATUS_PUBLISHED)
                        .orderByDesc(ProcessPackage::getVersion)
                        .last("LIMIT 1"));
        if (pkg == null) {
            throw new BizException(40400, "PUBLISHED_PACKAGE_NOT_FOUND");
        }
        return pkg;
    }

    private ProcessPackage requirePackage(String id) {
        ProcessPackage pkg = processPackageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException(40400, "PROCESS_PACKAGE_NOT_FOUND");
        }
        return pkg;
    }

    private void requireDraft(ProcessPackage pkg) {
        if (!STATUS_DRAFT.equals(pkg.getStatus())) {
            throw new BizException(40000, "ONLY_DRAFT_CAN_BE_MODIFIED");
        }
    }

    private void ensureNoDraft(String processKey) {
        Long drafts = processPackageMapper.selectCount(
                new LambdaQueryWrapper<ProcessPackage>()
                        .eq(ProcessPackage::getProcessKey, processKey)
                        .eq(ProcessPackage::getStatus, STATUS_DRAFT));
        if (drafts > 0) {
            throw new BizException(40900, "PROCESS_DRAFT_ALREADY_EXISTS");
        }
    }

    private String normalizeCategory(String category) {
        String value = normalizeBlank(category);
        if (value == null) {
            return "approval";
        }
        if (!value.equals("approval") && !value.equals("business") && !value.equals("integration")) {
            throw new BizException(40000, "INVALID_PROCESS_CATEGORY");
        }
        return value;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private FormSnapshot extractInlineFormSnapshot(String processJson) {
        try {
            JsonNode root = objectMapper.readTree(processJson);
            if (root == null || !root.isObject() || !root.has("flow")) {
                throw new BizException(40000, "INVALID_PROCESS_JSON");
            }
            JsonNode form = root.get("form");
            if (form == null || !form.isObject()) {
                return new FormSnapshot(null, null, null);
            }
            String schema = form.has("schema")
                    ? objectMapper.writeValueAsString(form.get("schema"))
                    : null;
            String uiSchema = form.has("uiSchema")
                    ? objectMapper.writeValueAsString(form.get("uiSchema"))
                    : null;
            return new FormSnapshot(schema, uiSchema, null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40000, "INVALID_PROCESS_JSON");
        }
    }

    private ProcessPackageResponse toResponse(ProcessPackage pkg) {
        ProcessPackageResponse resp = new ProcessPackageResponse();
        resp.setId(pkg.getId());
        resp.setProcessKey(pkg.getProcessKey());
        resp.setName(pkg.getName());
        resp.setCategory(pkg.getCategory());
        resp.setDescription(pkg.getDescription());
        resp.setVersion(pkg.getVersion());
        resp.setStatus(pkg.getStatus());
        resp.setProcessJson(pkg.getProcessJson());
        resp.setFormSchema(pkg.getFormSchema());
        resp.setFormUiSchema(pkg.getFormUiSchema());
        resp.setFormDefinitionId(pkg.getFormDefinitionId());
        resp.setFormDefinitionVersion(pkg.getFormDefinitionVersion());
        resp.setSourcePackageId(pkg.getSourcePackageId());
        resp.setPublishedAt(pkg.getPublishedAt());
        resp.setCreatedAt(pkg.getCreatedAt());
        resp.setUpdatedAt(pkg.getUpdatedAt());
        return resp;
    }

    private FormSnapshot loadPublishedFormSnapshot(String formDefinitionId) {
        R<PublishedFormSnapshotResponse> response = lowcodeFormClient.getPublishedForm(formDefinitionId);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new BizException(50200, "PUBLISHED_FORM_SNAPSHOT_UNAVAILABLE");
        }
        PublishedFormSnapshotResponse form = response.getData();
        if (!formDefinitionId.equals(form.getFormDefinitionId()) || form.getVersion() == null) {
            throw new BizException(50200, "INVALID_PUBLISHED_FORM_SNAPSHOT");
        }
        return new FormSnapshot(form.getSchemaJson(), form.getUiSchemaJson(), form.getVersion());
    }

    private record FormSnapshot(String schema, String uiSchema, Integer version) {
    }
}
