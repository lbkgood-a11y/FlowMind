package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.dto.ApplicationActionResponse;
import com.triobase.service.lowcode.dto.ApplicationPageRequest;
import com.triobase.service.lowcode.dto.ApplicationPageResponse;
import com.triobase.service.lowcode.dto.ApplicationResponse;
import com.triobase.service.lowcode.dto.CreateApplicationRequest;
import com.triobase.service.lowcode.dto.UpdateApplicationRequest;
import com.triobase.service.lowcode.entity.LcApplication;
import com.triobase.service.lowcode.entity.LcApplicationAction;
import com.triobase.service.lowcode.entity.LcApplicationPage;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.mapper.ApplicationActionMapper;
import com.triobase.service.lowcode.mapper.ApplicationMapper;
import com.triobase.service.lowcode.mapper.ApplicationPageMapper;
import com.triobase.service.lowcode.mapper.ApplicationVersionMapper;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String GLOBAL_TENANT_ID = "GLOBAL";

    private final ApplicationMapper applicationMapper;
    private final ApplicationVersionMapper applicationVersionMapper;
    private final ApplicationPageMapper applicationPageMapper;
    private final ApplicationActionMapper applicationActionMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ApplicationMetadataValidator metadataValidator;
    private final ApplicationReferenceValidator referenceValidator;

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request, String operator) {
        if (request == null || !StringUtils.hasText(request.getAppKey())
                || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getPrimaryFormDefinitionId())) {
            throw new BizException(40051, "APPLICATION_REQUIRED");
        }
        String tenantId = currentTenantId();
        if (applicationMapper.selectCount(new LambdaQueryWrapper<LcApplication>()
                .eq(LcApplication::getTenantId, tenantId)
                .eq(LcApplication::getAppKey, request.getAppKey().trim())) > 0) {
            throw new BizException(40052, "APPLICATION_KEY_ALREADY_EXISTS");
        }
        metadataValidator.validateDraft(request.getPages(), request.getActions());
        LcFormDefinition form = findVisibleForm(request.getPrimaryFormDefinitionId());
        LocalDateTime now = LocalDateTime.now();
        String user = operator(operator);

        LcApplication application = new LcApplication();
        application.setId(UlidGenerator.nextUlid());
        application.setTenantId(tenantId);
        application.setAppKey(request.getAppKey().trim());
        application.setName(request.getName().trim());
        application.setDescription(request.getDescription());
        application.setStatus(STATUS_DRAFT);
        application.setLatestVersion(1);
        application.setCreatedBy(user);
        application.setCreatedAt(now);
        application.setUpdatedBy(user);
        application.setUpdatedAt(now);
        applicationMapper.insert(application);

        LcApplicationVersion version = buildVersion(application, request.getName().trim(),
                request.getDescription(), form, request.getViewPermissionCode(), 1, null, user, now);
        applicationVersionMapper.insert(version);
        replacePages(version.getId(), tenantId, request.getPages(), user, now);
        replaceActions(version.getId(), tenantId, request.getActions(), user, now);
        return getVersion(version.getId());
    }

    public PageResult<ApplicationResponse> list(int page, int size) {
        List<ApplicationResponse> all = applicationMapper.selectList(new LambdaQueryWrapper<LcApplication>()
                        .in(LcApplication::getTenantId, visibleTenantIds())
                        .orderByDesc(LcApplication::getUpdatedAt))
                .stream()
                .map(this::latestResponse)
                .toList();
        int fromIndex = Math.max((page - 1) * size, 0);
        if (fromIndex >= all.size()) {
            return PageResult.empty(page, size);
        }
        int toIndex = Math.min(fromIndex + size, all.size());
        return PageResult.of(all.subList(fromIndex, toIndex), all.size(), page, size);
    }

    public ApplicationResponse getVersion(String versionId) {
        LcApplicationVersion version = findVisibleVersion(versionId);
        if (version == null) {
            throw new BizException(40450, "APPLICATION_VERSION_NOT_FOUND");
        }
        LcApplication application = applicationMapper.selectById(version.getApplicationId());
        return toResponse(application, version, listPages(version.getId()), listActions(version.getId()));
    }

    @Transactional
    public ApplicationResponse update(String versionId, UpdateApplicationRequest request, String operator) {
        LcApplicationVersion version = requireMutableVersion(versionId);
        if (!STATUS_DRAFT.equals(version.getStatus())) {
            throw new BizException(40053, "ONLY_DRAFT_APPLICATION_CAN_BE_MODIFIED");
        }
        if (request == null) {
            throw new BizException(40051, "APPLICATION_UPDATE_REQUIRED");
        }
        List<ApplicationPageRequest> nextPages = request.getPages() != null
                ? request.getPages() : listPages(version.getId()).stream().map(this::toPageRequest).toList();
        List<ApplicationActionRequest> nextActions = request.getActions() != null
                ? request.getActions() : listActions(version.getId()).stream().map(this::toActionRequest).toList();
        metadataValidator.validateDraft(nextPages, nextActions);
        LcFormDefinition form = StringUtils.hasText(request.getPrimaryFormDefinitionId())
                ? findVisibleForm(request.getPrimaryFormDefinitionId())
                : findVisibleForm(version.getPrimaryFormDefinitionId());
        LocalDateTime now = LocalDateTime.now();
        String user = operator(operator);
        String nextName = StringUtils.hasText(request.getName()) ? request.getName().trim() : version.getName();

        version.setName(nextName);
        version.setDescription(request.getDescription() != null ? request.getDescription() : version.getDescription());
        version.setPrimaryFormDefinitionId(form.getId());
        version.setFormKey(form.getFormKey());
        version.setFormVersion(form.getVersion());
        version.setSchemaHash(form.getSchemaHash());
        version.setViewPermissionCode(request.getViewPermissionCode() != null
                ? request.getViewPermissionCode() : version.getViewPermissionCode());
        version.setMetadataHash(null);
        version.setUpdatedBy(user);
        version.setUpdatedAt(now);
        applicationVersionMapper.updateById(version);

        LcApplication application = applicationMapper.selectById(version.getApplicationId());
        application.setName(nextName);
        application.setDescription(version.getDescription());
        application.setStatus(STATUS_DRAFT);
        application.setUpdatedBy(user);
        application.setUpdatedAt(now);
        applicationMapper.updateById(application);

        if (request.getPages() != null) {
            replacePages(version.getId(), version.getTenantId(), request.getPages(), user, now);
        }
        if (request.getActions() != null) {
            replaceActions(version.getId(), version.getTenantId(), request.getActions(), user, now);
        }
        return getVersion(versionId);
    }

    @Transactional
    public ApplicationResponse deriveNewVersion(String sourceVersionId, String operator) {
        LcApplicationVersion source = findVisibleVersion(sourceVersionId);
        if (source == null) {
            throw new BizException(40450, "APPLICATION_VERSION_NOT_FOUND");
        }
        if (STATUS_DRAFT.equals(source.getStatus())) {
            throw new BizException(40053, "DRAFT_APPLICATION_CANNOT_DERIVE_VERSION");
        }
        String tenantId = currentTenantId();
        ensureNoDraft(tenantId, source.getAppKey());
        LcApplication application = applicationMapper.selectById(source.getApplicationId());
        int nextVersion = nextVersion(tenantId, source.getAppKey(), source.getVersion());
        LocalDateTime now = LocalDateTime.now();
        String user = operator(operator);

        LcApplicationVersion draft = new LcApplicationVersion();
        draft.setId(UlidGenerator.nextUlid());
        draft.setTenantId(tenantId);
        draft.setApplicationId(application.getId());
        draft.setAppKey(source.getAppKey());
        draft.setVersion(nextVersion);
        draft.setStatus(STATUS_DRAFT);
        draft.setName(source.getName());
        draft.setDescription(source.getDescription());
        draft.setPrimaryFormDefinitionId(source.getPrimaryFormDefinitionId());
        draft.setFormKey(source.getFormKey());
        draft.setFormVersion(source.getFormVersion());
        draft.setSchemaHash(source.getSchemaHash());
        draft.setViewPermissionCode(source.getViewPermissionCode());
        draft.setSourceApplicationVersionId(source.getId());
        draft.setCreatedBy(user);
        draft.setCreatedAt(now);
        draft.setUpdatedBy(user);
        draft.setUpdatedAt(now);
        applicationVersionMapper.insert(draft);
        replacePages(draft.getId(), tenantId, listPages(source.getId()).stream().map(this::toPageRequest).toList(), user, now);
        replaceActions(draft.getId(), tenantId, listActions(source.getId()).stream().map(this::toActionRequest).toList(), user, now);

        application.setStatus(STATUS_DRAFT);
        application.setLatestVersion(nextVersion);
        application.setUpdatedBy(user);
        application.setUpdatedAt(now);
        applicationMapper.updateById(application);
        return getVersion(draft.getId());
    }

    @Transactional
    public ApplicationResponse publish(String versionId) {
        LcApplicationVersion version = requireMutableVersion(versionId);
        if (!STATUS_DRAFT.equals(version.getStatus())) {
            throw new BizException(40053, "ONLY_DRAFT_APPLICATION_CAN_BE_PUBLISHED");
        }
        LcFormDefinition form = findVisibleForm(version.getPrimaryFormDefinitionId());
        if (!STATUS_PUBLISHED.equals(form.getStatus())) {
            throw new BizException(40950, "APPLICATION_FORM_NOT_PUBLISHED");
        }
        List<ApplicationPageRequest> pages = listPages(version.getId()).stream().map(this::toPageRequest).toList();
        List<ApplicationActionRequest> actions = listActions(version.getId()).stream().map(this::toActionRequest).toList();
        metadataValidator.validateDraft(pages, actions);
        metadataValidator.validateFieldReferences(form.getSchemaJson(), pages);
        referenceValidator.validatePublication(version, actions);
        LocalDateTime now = LocalDateTime.now();
        String metadataHash = metadataHash(version, pages, actions);

        version.setStatus(STATUS_PUBLISHED);
        version.setFormVersion(form.getVersion());
        version.setSchemaHash(form.getSchemaHash());
        version.setMetadataHash(metadataHash);
        version.setPublishedAt(now);
        version.setUpdatedAt(now);
        applicationVersionMapper.updateById(version);

        LcApplication application = applicationMapper.selectById(version.getApplicationId());
        application.setStatus(STATUS_PUBLISHED);
        application.setLatestVersion(version.getVersion());
        application.setLatestPublishedVersionId(version.getId());
        application.setUpdatedAt(now);
        applicationMapper.updateById(application);
        return getVersion(versionId);
    }

    @Transactional
    public ApplicationResponse offline(String versionId) {
        LcApplicationVersion version = requireMutableVersion(versionId);
        if (!STATUS_PUBLISHED.equals(version.getStatus())) {
            throw new BizException(40053, "ONLY_PUBLISHED_APPLICATION_CAN_BE_OFFLINE");
        }
        LocalDateTime now = LocalDateTime.now();
        version.setStatus(STATUS_OFFLINE);
        version.setOfflineAt(now);
        version.setUpdatedAt(now);
        applicationVersionMapper.updateById(version);

        LcApplication application = applicationMapper.selectById(version.getApplicationId());
        if (version.getId().equals(application.getLatestPublishedVersionId())) {
            LcApplicationVersion latestPublished = applicationVersionMapper.selectList(
                            new LambdaQueryWrapper<LcApplicationVersion>()
                                    .eq(LcApplicationVersion::getTenantId, version.getTenantId())
                                    .eq(LcApplicationVersion::getAppKey, version.getAppKey())
                                    .eq(LcApplicationVersion::getStatus, STATUS_PUBLISHED)
                                    .orderByDesc(LcApplicationVersion::getVersion))
                    .stream()
                    .findFirst()
                    .orElse(null);
            application.setLatestPublishedVersionId(latestPublished != null ? latestPublished.getId() : null);
            application.setStatus(latestPublished != null ? STATUS_PUBLISHED : STATUS_OFFLINE);
        }
        application.setUpdatedAt(now);
        applicationMapper.updateById(application);
        return getVersion(versionId);
    }

    private ApplicationResponse latestResponse(LcApplication application) {
        LcApplicationVersion version = applicationVersionMapper.selectList(new LambdaQueryWrapper<LcApplicationVersion>()
                        .eq(LcApplicationVersion::getApplicationId, application.getId())
                        .orderByDesc(LcApplicationVersion::getVersion))
                .stream()
                .findFirst()
                .orElse(null);
        return toResponse(application, version,
                version != null ? listPages(version.getId()) : List.of(),
                version != null ? listActions(version.getId()) : List.of());
    }

    private LcApplicationVersion buildVersion(LcApplication application,
                                              String name,
                                              String description,
                                              LcFormDefinition form,
                                              String viewPermissionCode,
                                              int versionNumber,
                                              String sourceVersionId,
                                              String user,
                                              LocalDateTime now) {
        LcApplicationVersion version = new LcApplicationVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setTenantId(application.getTenantId());
        version.setApplicationId(application.getId());
        version.setAppKey(application.getAppKey());
        version.setVersion(versionNumber);
        version.setStatus(STATUS_DRAFT);
        version.setName(name);
        version.setDescription(description);
        version.setPrimaryFormDefinitionId(form.getId());
        version.setFormKey(form.getFormKey());
        version.setFormVersion(form.getVersion());
        version.setSchemaHash(form.getSchemaHash());
        version.setViewPermissionCode(viewPermissionCode);
        version.setSourceApplicationVersionId(sourceVersionId);
        version.setCreatedBy(user);
        version.setCreatedAt(now);
        version.setUpdatedBy(user);
        version.setUpdatedAt(now);
        return version;
    }

    private void replacePages(String versionId,
                              String tenantId,
                              List<ApplicationPageRequest> pages,
                              String user,
                              LocalDateTime now) {
        applicationPageMapper.delete(new LambdaQueryWrapper<LcApplicationPage>()
                .eq(LcApplicationPage::getApplicationVersionId, versionId));
        if (pages == null) {
            return;
        }
        for (ApplicationPageRequest page : pages) {
            LcApplicationPage entity = new LcApplicationPage();
            entity.setId(UlidGenerator.nextUlid());
            entity.setTenantId(tenantId);
            entity.setApplicationVersionId(versionId);
            entity.setPageType(page.getPageType().trim().toUpperCase());
            entity.setMetadataJson(page.getMetadataJson());
            entity.setSortOrder(page.getSortOrder() != null ? page.getSortOrder() : 0);
            entity.setCreatedBy(user);
            entity.setCreatedAt(now);
            entity.setUpdatedBy(user);
            entity.setUpdatedAt(now);
            applicationPageMapper.insert(entity);
        }
    }

    private void replaceActions(String versionId,
                                String tenantId,
                                List<ApplicationActionRequest> actions,
                                String user,
                                LocalDateTime now) {
        applicationActionMapper.delete(new LambdaQueryWrapper<LcApplicationAction>()
                .eq(LcApplicationAction::getApplicationVersionId, versionId));
        if (actions == null) {
            return;
        }
        for (ApplicationActionRequest action : actions) {
            LcApplicationAction entity = new LcApplicationAction();
            entity.setId(UlidGenerator.nextUlid());
            entity.setTenantId(tenantId);
            entity.setApplicationVersionId(versionId);
            entity.setActionCode(action.getActionCode().trim());
            entity.setActionType(action.getActionType().trim().toUpperCase());
            entity.setLabel(action.getLabel().trim());
            entity.setPermissionCode(action.getPermissionCode());
            entity.setFormDefinitionId(action.getFormDefinitionId());
            entity.setProcessKey(action.getProcessKey());
            entity.setMetadataJson(action.getMetadataJson());
            entity.setStatus(StringUtils.hasText(action.getStatus()) ? action.getStatus().trim() : "ENABLED");
            entity.setSortOrder(action.getSortOrder() != null ? action.getSortOrder() : 0);
            entity.setCreatedBy(user);
            entity.setCreatedAt(now);
            entity.setUpdatedBy(user);
            entity.setUpdatedAt(now);
            applicationActionMapper.insert(entity);
        }
    }

    private LcApplicationVersion requireMutableVersion(String versionId) {
        LcApplicationVersion version = findVisibleVersion(versionId);
        if (version == null) {
            throw new BizException(40450, "APPLICATION_VERSION_NOT_FOUND");
        }
        if (!canMutate(version)) {
            throw new BizException(40350, "APPLICATION_MUTATION_DENIED");
        }
        return version;
    }

    private LcApplicationVersion findVisibleVersion(String versionId) {
        return applicationVersionMapper.selectOne(new LambdaQueryWrapper<LcApplicationVersion>()
                .eq(LcApplicationVersion::getId, versionId)
                .in(LcApplicationVersion::getTenantId, visibleTenantIds()));
    }

    private LcFormDefinition findVisibleForm(String formDefinitionId) {
        LcFormDefinition form = formDefinitionMapper.selectOne(new LambdaQueryWrapper<LcFormDefinition>()
                .eq(LcFormDefinition::getId, formDefinitionId)
                .in(LcFormDefinition::getTenantId, visibleTenantIds()));
        if (form == null) {
            throw new BizException(40401, "FORM_DEFINITION_NOT_FOUND");
        }
        return form;
    }

    private List<LcApplicationPage> listPages(String versionId) {
        return applicationPageMapper.selectList(new LambdaQueryWrapper<LcApplicationPage>()
                .eq(LcApplicationPage::getApplicationVersionId, versionId)
                .orderByAsc(LcApplicationPage::getSortOrder));
    }

    private List<LcApplicationAction> listActions(String versionId) {
        return applicationActionMapper.selectList(new LambdaQueryWrapper<LcApplicationAction>()
                .eq(LcApplicationAction::getApplicationVersionId, versionId)
                .orderByAsc(LcApplicationAction::getSortOrder));
    }

    private void ensureNoDraft(String tenantId, String appKey) {
        if (applicationVersionMapper.selectCount(new LambdaQueryWrapper<LcApplicationVersion>()
                .eq(LcApplicationVersion::getTenantId, tenantId)
                .eq(LcApplicationVersion::getAppKey, appKey)
                .eq(LcApplicationVersion::getStatus, STATUS_DRAFT)) > 0) {
            throw new BizException(40950, "APPLICATION_DRAFT_ALREADY_EXISTS");
        }
    }

    private int nextVersion(String tenantId, String appKey, Integer sourceVersion) {
        int maxVersion = applicationVersionMapper.selectList(new LambdaQueryWrapper<LcApplicationVersion>()
                        .eq(LcApplicationVersion::getTenantId, tenantId)
                        .eq(LcApplicationVersion::getAppKey, appKey))
                .stream()
                .map(LcApplicationVersion::getVersion)
                .filter(version -> version != null)
                .max(Integer::compareTo)
                .orElse(0);
        return Math.max(maxVersion, sourceVersion != null ? sourceVersion : 0) + 1;
    }

    private boolean canMutate(LcApplicationVersion version) {
        String tenantId = currentTenantId();
        if (tenantId.equals(version.getTenantId())) {
            return true;
        }
        return GLOBAL_TENANT_ID.equals(version.getTenantId())
                && SecurityContextHolder.getRoles().stream().anyMatch("ADMIN"::equals);
    }

    private ApplicationResponse toResponse(LcApplication application,
                                           LcApplicationVersion version,
                                           List<LcApplicationPage> pages,
                                           List<LcApplicationAction> actions) {
        ApplicationResponse response = new ApplicationResponse();
        response.setId(application.getId());
        response.setTenantId(application.getTenantId());
        response.setAppKey(application.getAppKey());
        response.setName(application.getName());
        response.setDescription(application.getDescription());
        response.setStatus(version != null ? version.getStatus() : application.getStatus());
        response.setLatestVersion(application.getLatestVersion());
        response.setLatestPublishedVersionId(application.getLatestPublishedVersionId());
        response.setCreatedAt(application.getCreatedAt());
        if (version != null) {
            response.setVersionId(version.getId());
            response.setVersion(version.getVersion());
            response.setPrimaryFormDefinitionId(version.getPrimaryFormDefinitionId());
            response.setFormKey(version.getFormKey());
            response.setFormVersion(version.getFormVersion());
            response.setSchemaHash(version.getSchemaHash());
            response.setViewPermissionCode(version.getViewPermissionCode());
            response.setMetadataHash(version.getMetadataHash());
            response.setPublishedAt(version.getPublishedAt());
            response.setOfflineAt(version.getOfflineAt());
        }
        response.setPages(pages.stream().map(this::toPageResponse).toList());
        response.setActions(actions.stream().map(this::toActionResponse).toList());
        return response;
    }

    private ApplicationPageResponse toPageResponse(LcApplicationPage page) {
        ApplicationPageResponse response = new ApplicationPageResponse();
        response.setId(page.getId());
        response.setPageType(page.getPageType());
        response.setMetadataJson(page.getMetadataJson());
        response.setSortOrder(page.getSortOrder());
        return response;
    }

    private ApplicationActionResponse toActionResponse(LcApplicationAction action) {
        ApplicationActionResponse response = new ApplicationActionResponse();
        response.setId(action.getId());
        response.setActionCode(action.getActionCode());
        response.setActionType(action.getActionType());
        response.setLabel(action.getLabel());
        response.setPermissionCode(action.getPermissionCode());
        response.setFormDefinitionId(action.getFormDefinitionId());
        response.setProcessKey(action.getProcessKey());
        response.setMetadataJson(action.getMetadataJson());
        response.setStatus(action.getStatus());
        response.setSortOrder(action.getSortOrder());
        return response;
    }

    private ApplicationPageRequest toPageRequest(LcApplicationPage page) {
        ApplicationPageRequest request = new ApplicationPageRequest();
        request.setPageType(page.getPageType());
        request.setMetadataJson(page.getMetadataJson());
        request.setSortOrder(page.getSortOrder());
        return request;
    }

    private ApplicationActionRequest toActionRequest(LcApplicationAction action) {
        ApplicationActionRequest request = new ApplicationActionRequest();
        request.setActionCode(action.getActionCode());
        request.setActionType(action.getActionType());
        request.setLabel(action.getLabel());
        request.setPermissionCode(action.getPermissionCode());
        request.setFormDefinitionId(action.getFormDefinitionId());
        request.setProcessKey(action.getProcessKey());
        request.setMetadataJson(action.getMetadataJson());
        request.setStatus(action.getStatus());
        request.setSortOrder(action.getSortOrder());
        return request;
    }

    private String metadataHash(LcApplicationVersion version,
                                List<ApplicationPageRequest> pages,
                                List<ApplicationActionRequest> actions) {
        String payload = version.getAppKey() + ":" + version.getVersion()
                + ":" + version.getPrimaryFormDefinitionId()
                + ":" + version.getSchemaHash()
                + ":" + pages
                + ":" + actions;
        return DigestUtils.md5DigestAsHex(payload.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> visibleTenantIds() {
        String tenantId = currentTenantId();
        if (GLOBAL_TENANT_ID.equals(tenantId)) {
            return List.of(GLOBAL_TENANT_ID);
        }
        return List.of(tenantId, GLOBAL_TENANT_ID);
    }

    private String currentTenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
    }

    private String operator(String operator) {
        if (StringUtils.hasText(operator)) {
            return operator;
        }
        String username = SecurityContextHolder.getUsername();
        return StringUtils.hasText(username) ? username : "system";
    }
}
