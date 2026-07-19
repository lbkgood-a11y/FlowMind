package com.triobase.service.action.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.util.ActionTypeValidator;
import com.triobase.service.action.dto.ActionDefinitionDiagnostic;
import com.triobase.service.action.dto.ActionDefinitionSyncRequest;
import com.triobase.service.action.dto.ActionDefinitionSyncResponse;
import com.triobase.service.action.entity.ActionDefinitionSnapshot;
import com.triobase.service.action.exception.ActionRuntimeException;
import com.triobase.service.action.mapper.ActionDefinitionSnapshotMapper;
import com.triobase.service.action.support.ActionJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionDefinitionSyncService {

    private final ActionDefinitionRegistry registry;
    private final ActionDefinitionSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public ActionDefinitionSyncResponse sync(ActionDefinitionSyncRequest request) {
        if (request == null || !StringUtils.hasText(request.getOwnerService())
                || request.getDefinitions() == null || request.getDefinitions().isEmpty()) {
            throw new ActionRuntimeException(
                    40046,
                    ActionErrorCategory.VALIDATION,
                    "ACTION_DEFINITION_SYNC_REQUIRED");
        }
        ActionDefinitionSyncResponse response = new ActionDefinitionSyncResponse();
        response.setOwnerService(request.getOwnerService().trim());
        for (ActionDefinition definition : request.getDefinitions()) {
            normalize(response.getOwnerService(), definition);
            registry.registerOrReplace(definition);
            int version = persistSnapshot(definition);
            response.getVersions().put(definition.getActionType(), version);
            response.getDiagnostics().add(diagnostic(definition.getActionType()));
        }
        response.setSynchronizedCount(response.getVersions().size());
        return response;
    }

    public List<ActionDefinitionDiagnostic> diagnostics() {
        return registry.all().stream()
                .map(ActionDefinition::getActionType)
                .sorted()
                .map(this::diagnostic)
                .toList();
    }

    public ActionDefinitionDiagnostic diagnostic(String actionType) {
        String normalized = ActionTypeValidator.requireValid(actionType);
        ActionDefinitionDiagnostic diagnostic = new ActionDefinitionDiagnostic();
        diagnostic.setActionType(normalized);
        registry.find(normalized).ifPresent(definition -> {
            diagnostic.setRegistered(true);
            diagnostic.setOwnerService(definition.getOwnerService());
            diagnostic.setTargetType(definition.getTargetType());
        });
        List<ActionDefinitionSnapshot> snapshots = snapshots(normalized);
        diagnostic.setSnapshotExists(!snapshots.isEmpty());
        diagnostic.setVersions(snapshots.stream()
                .map(ActionDefinitionSnapshot::getVersion)
                .filter(version -> version != null)
                .toList());
        snapshots.stream()
                .filter(snapshot -> snapshot.getVersion() != null)
                .reduce((first, second) -> second)
                .ifPresent(snapshot -> {
                    diagnostic.setLatestVersion(snapshot.getVersion());
                    diagnostic.setLatestSchemaHash(snapshot.getSchemaHash());
                    if (diagnostic.getOwnerService() == null) {
                        diagnostic.setOwnerService(snapshot.getOwnerService());
                    } else if (!diagnostic.getOwnerService().equals(snapshot.getOwnerService())) {
                        diagnostic.setOwnerCompatible(false);
                        diagnostic.getIssues().add("ACTION_DEFINITION_OWNER_INCOMPATIBLE");
                    }
                    if (diagnostic.getTargetType() == null) {
                        diagnostic.setTargetType(snapshot.getTargetType());
                    }
                });
        long distinctVersions = diagnostic.getVersions().stream().distinct().count();
        diagnostic.setDuplicateVersions(distinctVersions != diagnostic.getVersions().size());
        if (diagnostic.isDuplicateVersions()) {
            diagnostic.getIssues().add("ACTION_DEFINITION_DUPLICATE_VERSION");
        }
        if (!diagnostic.isRegistered()) {
            diagnostic.getIssues().add("ACTION_DEFINITION_RUNTIME_MISSING");
        }
        if (!diagnostic.isSnapshotExists()) {
            diagnostic.getIssues().add("ACTION_DEFINITION_SNAPSHOT_MISSING");
        }
        diagnostic.setCompatible(diagnostic.getIssues().isEmpty());
        return diagnostic;
    }

    private void normalize(String ownerService, ActionDefinition definition) {
        if (definition == null) {
            throw new ActionRuntimeException(
                    40043,
                    ActionErrorCategory.VALIDATION,
                    "ACTION_DEFINITION_REQUIRED");
        }
        ActionTypeValidator.requireValid(definition.getActionType());
        if (!StringUtils.hasText(definition.getOwnerService())) {
            definition.setOwnerService(ownerService);
        }
        if (!ownerService.equals(definition.getOwnerService())) {
            throw new ActionRuntimeException(
                    40047,
                    ActionErrorCategory.VALIDATION,
                    "ACTION_DEFINITION_OWNER_MISMATCH",
                    "ownerService",
                    null);
        }
    }

    private int persistSnapshot(ActionDefinition definition) {
        String definitionJson = ActionJsonSupport.boundedJson(objectMapper, definition, 64_000);
        String schemaHash = sha256(definitionJson);
        Integer latest = latestVersion(definition.getActionType());
        int version = latest == null ? 1 : latest + 1;

        ActionDefinitionSnapshot snapshot = new ActionDefinitionSnapshot();
        snapshot.setActionType(definition.getActionType());
        snapshot.setOwnerService(definition.getOwnerService());
        snapshot.setTargetType(definition.getTargetType());
        snapshot.setVersion(version);
        snapshot.setStatus("ACTIVE");
        snapshot.setDefinitionJson(definitionJson);
        snapshot.setSchemaHash(schemaHash);
        snapshot.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
        snapshotMapper.insert(snapshot);
        return version;
    }

    private Integer latestVersion(String actionType) {
        List<ActionDefinitionSnapshot> snapshots = snapshots(actionType);
        return snapshots.stream()
                .map(ActionDefinitionSnapshot::getVersion)
                .filter(version -> version != null)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private List<ActionDefinitionSnapshot> snapshots(String actionType) {
        return snapshotMapper.selectList(
                new LambdaQueryWrapper<ActionDefinitionSnapshot>()
                        .eq(ActionDefinitionSnapshot::getActionType, actionType)
                        .orderByAsc(ActionDefinitionSnapshot::getVersion));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
