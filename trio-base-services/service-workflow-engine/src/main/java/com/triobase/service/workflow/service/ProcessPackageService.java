package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.workflow.dto.CreateProcessPackageRequest;
import com.triobase.service.workflow.dto.ProcessPackageResponse;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProcessPackageService {

    private final ProcessPackageMapper processPackageMapper;

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
        if (!StringUtils.hasText(request.getProcessKey()) || !StringUtils.hasText(request.getName())) {
            throw new BizException(40000, "PROCESS_KEY_OR_NAME_REQUIRED");
        }

        // 检查 processKey 唯一性
        Long exists = processPackageMapper.selectCount(
                new LambdaQueryWrapper<ProcessPackage>()
                        .eq(ProcessPackage::getProcessKey, request.getProcessKey()));
        if (exists > 0) {
            throw new BizException(40000, "PROCESS_KEY_ALREADY_EXISTS");
        }

        // 从 processJson 中抽取 formSchema 和 formUiSchema
        String formSchema = null;
        String formUiSchema = null;
        if (StringUtils.hasText(request.getProcessJson())) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = om.readTree(request.getProcessJson());
                if (root.has("form")) {
                    if (root.get("form").has("schema")) {
                        formSchema = om.writeValueAsString(root.get("form").get("schema"));
                    }
                    if (root.get("form").has("uiSchema")) {
                        formUiSchema = om.writeValueAsString(root.get("form").get("uiSchema"));
                    }
                }
            } catch (Exception e) {
                throw new BizException(40000, "INVALID_PROCESS_JSON");
            }
        }

        ProcessPackage pkg = new ProcessPackage();
        pkg.setId(UlidGenerator.nextUlid());
        pkg.setProcessKey(request.getProcessKey().trim());
        pkg.setName(request.getName().trim());
        pkg.setCategory(request.getCategory() != null ? request.getCategory() : "approval");
        pkg.setDescription(request.getDescription());
        pkg.setVersion(1);
        pkg.setStatus("DRAFT");
        pkg.setProcessJson(request.getProcessJson());
        pkg.setFormSchema(formSchema);
        pkg.setFormUiSchema(formUiSchema);
        processPackageMapper.insert(pkg);
        return toResponse(pkg);
    }

    @Transactional
    public ProcessPackageResponse publish(String id) {
        ProcessPackage pkg = processPackageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException(40400, "PROCESS_PACKAGE_NOT_FOUND");
        }
        if (!"DRAFT".equals(pkg.getStatus())) {
            throw new BizException(40000, "ONLY_DRAFT_CAN_BE_PUBLISHED");
        }
        pkg.setStatus("PUBLISHED");
        processPackageMapper.updateById(pkg);
        return toResponse(pkg);
    }

    @Transactional
    public ProcessPackageResponse offline(String id) {
        ProcessPackage pkg = processPackageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException(40400, "PROCESS_PACKAGE_NOT_FOUND");
        }
        pkg.setStatus("OFFLINE");
        processPackageMapper.updateById(pkg);
        return toResponse(pkg);
    }

    @Transactional
    public void delete(String id) {
        ProcessPackage pkg = processPackageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException(40400, "PROCESS_PACKAGE_NOT_FOUND");
        }
        processPackageMapper.deleteById(id);
    }

    /**
     * 获取已发布的最新版本流程包
     */
    public ProcessPackage findPublishedByKey(String processKey) {
        ProcessPackage pkg = processPackageMapper.selectOne(
                new LambdaQueryWrapper<ProcessPackage>()
                        .eq(ProcessPackage::getProcessKey, processKey)
                        .eq(ProcessPackage::getStatus, "PUBLISHED")
                        .orderByDesc(ProcessPackage::getVersion)
                        .last("LIMIT 1"));
        if (pkg == null) {
            throw new BizException(40400, "PUBLISHED_PACKAGE_NOT_FOUND");
        }
        return pkg;
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
        resp.setCreatedAt(pkg.getCreatedAt());
        resp.setUpdatedAt(pkg.getUpdatedAt());
        return resp;
    }
}
