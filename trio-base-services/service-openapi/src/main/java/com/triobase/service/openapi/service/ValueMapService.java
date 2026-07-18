package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ValueMapEntry;
import com.triobase.service.openapi.domain.entity.ValueMapSet;
import com.triobase.service.openapi.domain.entity.ValueMapVersion;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.UnmappedValuePolicy;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateValueMapRequest;
import com.triobase.service.openapi.dto.ValueMapVersionResponse;
import com.triobase.service.openapi.dto.ValueMapVersionRequest;
import com.triobase.service.openapi.infrastructure.mapper.ValueMapEntryMapper;
import com.triobase.service.openapi.infrastructure.mapper.ValueMapSetMapper;
import com.triobase.service.openapi.infrastructure.mapper.ValueMapVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ValueMapService {

    private static final int INVALID_VALUE_MAP = 40022;
    private static final int VALUE_MAP_NOT_FOUND = 40422;
    private static final int VALUE_UNMAPPED = 42220;
    private static final int VALUE_MAP_CONFLICT = 40922;

    private final ValueMapSetMapper setMapper;
    private final ValueMapVersionMapper versionMapper;
    private final ValueMapEntryMapper entryMapper;
    private final IntegrationAuditService auditService;

    @Transactional
    public ValueMapVersion create(CreateValueMapRequest request) {
        validateRequest(request);
        String tenantId = resolveTargetTenant(request.tenantId());
        if (setMapper.selectCount(new LambdaQueryWrapper<ValueMapSet>()
                .eq(ValueMapSet::getValueMapKey, request.valueMapKey().trim())
                .eq(tenantId != null, ValueMapSet::getTenantId, tenantId)
                .isNull(tenantId == null, ValueMapSet::getTenantId)) > 0) {
            throw new BizException(VALUE_MAP_CONFLICT, "OPENAPI_VALUE_MAP_ALREADY_EXISTS");
        }
        validateEntries(request.entries(), request.caseSensitive());
        LocalDateTime now = LocalDateTime.now();
        ValueMapSet set = new ValueMapSet();
        set.setId(UlidGenerator.nextUlid());
        set.setTenantId(tenantId);
        set.setValueMapKey(request.valueMapKey().trim());
        set.setDisplayName(request.displayName().trim());
        set.setDescription(request.description());
        set.setOwnerId(request.ownerId());
        set.setLifecycleState(AssetLifecycleState.ACTIVE);
        set.setRowVersion(0L);
        set.setCreatedBy(currentOperator());
        set.setCreatedAt(now);
        set.setUpdatedBy(currentOperator());
        set.setUpdatedAt(now);
        setMapper.insert(set);

        ValueMapVersion version = new ValueMapVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setValueMapSetId(set.getId());
        version.setVersionNumber(1);
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        version.setCaseSensitive(request.caseSensitive());
        version.setUnmappedPolicy(request.unmappedPolicy());
        version.setDefaultCanonicalValue(request.defaultCanonicalValue());
        version.setDefaultExternalValue(request.defaultExternalValue());
        version.setRowVersion(0L);
        version.setCreatedBy(currentOperator());
        version.setCreatedAt(now);
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(now);
        versionMapper.insert(version);
        insertEntries(version.getId(), request.entries(), now);
        auditService.success("VALUE_MAP_CREATED", "VALUE_MAP_VERSION", version.getId(),
                JsonNodeFactory.instance.objectNode().put("entryCount", request.entries().size()));
        return version;
    }

    @Transactional
    public ValueMapVersion publish(String versionId) {
        ValueMapVersion version = requireVersion(versionId);
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(VALUE_MAP_CONFLICT, "OPENAPI_ONLY_DRAFT_VALUE_MAP_CAN_BE_PUBLISHED");
        }
        if (version.getUnmappedPolicy() == UnmappedValuePolicy.USE_DEFAULT
                && (!StringUtils.hasText(version.getDefaultCanonicalValue())
                || !StringUtils.hasText(version.getDefaultExternalValue()))) {
            throw new BizException(INVALID_VALUE_MAP, "OPENAPI_VALUE_MAP_DEFAULTS_REQUIRED");
        }
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setPublishedBy(currentOperator());
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(LocalDateTime.now());
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(VALUE_MAP_CONFLICT, "OPENAPI_VALUE_MAP_VERSION_CONFLICT");
        }
        auditService.success("VALUE_MAP_PUBLISHED", "VALUE_MAP_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return version;
    }

    @Transactional
    public ValueMapVersion createDraft(String valueMapSetId, ValueMapVersionRequest request) {
        requireSet(valueMapSetId);
        validateVersionRequest(request);
        if (versionMapper.selectCount(new LambdaQueryWrapper<ValueMapVersion>()
                .eq(ValueMapVersion::getValueMapSetId, valueMapSetId)
                .eq(ValueMapVersion::getLifecycleState, VersionLifecycleState.DRAFT)) > 0) {
            throw new BizException(VALUE_MAP_CONFLICT, "OPENAPI_VALUE_MAP_DRAFT_ALREADY_EXISTS");
        }
        ValueMapVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<ValueMapVersion>()
                .eq(ValueMapVersion::getValueMapSetId, valueMapSetId)
                .orderByDesc(ValueMapVersion::getVersionNumber)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        ValueMapVersion draft = new ValueMapVersion();
        draft.setId(UlidGenerator.nextUlid());
        draft.setValueMapSetId(valueMapSetId);
        draft.setVersionNumber(latest == null ? 1 : latest.getVersionNumber() + 1);
        draft.setLifecycleState(VersionLifecycleState.DRAFT);
        applyRequest(draft, request);
        draft.setRowVersion(0L);
        draft.setCreatedBy(currentOperator());
        draft.setCreatedAt(now);
        draft.setUpdatedBy(currentOperator());
        draft.setUpdatedAt(now);
        versionMapper.insert(draft);
        insertEntries(draft.getId(), request.entries(), now);
        return draft;
    }

    @Transactional
    public ValueMapVersion updateDraft(String versionId, ValueMapVersionRequest request) {
        validateVersionRequest(request);
        ValueMapVersion version = requireVersion(versionId);
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(VALUE_MAP_CONFLICT, "OPENAPI_PUBLISHED_VALUE_MAP_IMMUTABLE");
        }
        applyRequest(version, request);
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(LocalDateTime.now());
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(VALUE_MAP_CONFLICT, "OPENAPI_VALUE_MAP_VERSION_CONFLICT");
        }
        entryMapper.delete(new LambdaQueryWrapper<ValueMapEntry>()
                .eq(ValueMapEntry::getValueMapVersionId, versionId));
        insertEntries(versionId, request.entries(), LocalDateTime.now());
        return version;
    }

    public String lookup(String versionId, String value, boolean canonicalToExternal) {
        ValueMapVersion version = requireVersion(versionId);
        if (version.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(VALUE_MAP_CONFLICT, "OPENAPI_VALUE_MAP_NOT_PUBLISHED");
        }
        List<ValueMapEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<ValueMapEntry>()
                .eq(ValueMapEntry::getValueMapVersionId, versionId));
        for (ValueMapEntry entry : entries) {
            String candidate = canonicalToExternal ? entry.getCanonicalValue() : entry.getExternalValue();
            if (matches(candidate, value, Boolean.TRUE.equals(version.getCaseSensitive()))) {
                return canonicalToExternal ? entry.getExternalValue() : entry.getCanonicalValue();
            }
        }
        return switch (version.getUnmappedPolicy()) {
            case PASS_THROUGH -> value;
            case USE_DEFAULT -> canonicalToExternal
                    ? version.getDefaultExternalValue() : version.getDefaultCanonicalValue();
            case FAIL -> throw new BizException(VALUE_UNMAPPED, "OPENAPI_VALUE_MAP_VALUE_UNMAPPED");
        };
    }

    public ValueMapVersionResponse getVersion(String versionId) {
        ValueMapVersion version = requireVersion(versionId);
        ValueMapSet set = requireSet(version.getValueMapSetId());
        List<CreateValueMapRequest.Entry> entries = entryMapper.selectList(new LambdaQueryWrapper<ValueMapEntry>()
                        .eq(ValueMapEntry::getValueMapVersionId, versionId)
                        .orderByAsc(ValueMapEntry::getEntryOrder))
                .stream()
                .map(entry -> new CreateValueMapRequest.Entry(
                        entry.getCanonicalValue(), entry.getExternalValue(), entry.getEntryOrder()))
                .toList();
        return new ValueMapVersionResponse(set.getId(), version.getId(), set.getTenantId(),
                set.getValueMapKey(), set.getDisplayName(), set.getOwnerId(), version.getVersionNumber(),
                version.getLifecycleState(), Boolean.TRUE.equals(version.getCaseSensitive()),
                version.getUnmappedPolicy(), version.getDefaultCanonicalValue(),
                version.getDefaultExternalValue(), entries);
    }

    private void validateRequest(CreateValueMapRequest request) {
        if (request == null || !StringUtils.hasText(request.valueMapKey())
                || !StringUtils.hasText(request.displayName())
                || !StringUtils.hasText(request.ownerId())
                || request.unmappedPolicy() == null || request.entries() == null) {
            throw new BizException(INVALID_VALUE_MAP, "OPENAPI_VALUE_MAP_REQUEST_INVALID");
        }
        if (request.unmappedPolicy() == UnmappedValuePolicy.USE_DEFAULT
                && (!StringUtils.hasText(request.defaultCanonicalValue())
                || !StringUtils.hasText(request.defaultExternalValue()))) {
            throw new BizException(INVALID_VALUE_MAP, "OPENAPI_VALUE_MAP_DEFAULTS_REQUIRED");
        }
    }

    private void validateEntries(List<CreateValueMapRequest.Entry> entries, boolean caseSensitive) {
        Set<String> canonical = new HashSet<>();
        Set<String> external = new HashSet<>();
        for (CreateValueMapRequest.Entry entry : entries) {
            if (entry == null || !StringUtils.hasText(entry.canonicalValue())
                    || !StringUtils.hasText(entry.externalValue())) {
                throw new BizException(INVALID_VALUE_MAP, "OPENAPI_VALUE_MAP_ENTRY_INVALID");
            }
            String canonicalKey = normalize(entry.canonicalValue(), caseSensitive);
            String externalKey = normalize(entry.externalValue(), caseSensitive);
            if (!canonical.add(canonicalKey) || !external.add(externalKey)) {
                throw new BizException(VALUE_MAP_CONFLICT, "OPENAPI_VALUE_MAP_ENTRY_DUPLICATE");
            }
        }
    }

    private void insertEntries(String versionId, List<CreateValueMapRequest.Entry> entries, LocalDateTime now) {
        for (CreateValueMapRequest.Entry request : entries) {
            ValueMapEntry entry = new ValueMapEntry();
            entry.setId(UlidGenerator.nextUlid());
            entry.setValueMapVersionId(versionId);
            entry.setCanonicalValue(request.canonicalValue());
            entry.setExternalValue(request.externalValue());
            entry.setEntryOrder(request.order());
            entry.setCreatedAt(now);
            entryMapper.insert(entry);
        }
    }

    private ValueMapVersion requireVersion(String versionId) {
        ValueMapVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(VALUE_MAP_NOT_FOUND, "OPENAPI_VALUE_MAP_VERSION_NOT_FOUND");
        }
        requireSet(version.getValueMapSetId());
        return version;
    }

    private ValueMapSet requireSet(String setId) {
        ValueMapSet set = setMapper.selectById(setId);
        String tenantId = SecurityContextHolder.getTenantId();
        if (set == null || (tenantId != null && !tenantId.equals(set.getTenantId()))) {
            throw new BizException(VALUE_MAP_NOT_FOUND, "OPENAPI_VALUE_MAP_NOT_FOUND");
        }
        return set;
    }

    private void validateVersionRequest(ValueMapVersionRequest request) {
        if (request == null || request.unmappedPolicy() == null || request.entries() == null) {
            throw new BizException(INVALID_VALUE_MAP, "OPENAPI_VALUE_MAP_REQUEST_INVALID");
        }
        if (request.unmappedPolicy() == UnmappedValuePolicy.USE_DEFAULT
                && (!StringUtils.hasText(request.defaultCanonicalValue())
                || !StringUtils.hasText(request.defaultExternalValue()))) {
            throw new BizException(INVALID_VALUE_MAP, "OPENAPI_VALUE_MAP_DEFAULTS_REQUIRED");
        }
        validateEntries(request.entries(), request.caseSensitive());
    }

    private void applyRequest(ValueMapVersion version, ValueMapVersionRequest request) {
        version.setCaseSensitive(request.caseSensitive());
        version.setUnmappedPolicy(request.unmappedPolicy());
        version.setDefaultCanonicalValue(request.defaultCanonicalValue());
        version.setDefaultExternalValue(request.defaultExternalValue());
    }

    private boolean matches(String left, String right, boolean caseSensitive) {
        return caseSensitive ? left.equals(right) : left.equalsIgnoreCase(right);
    }

    private String normalize(String value, boolean caseSensitive) {
        return caseSensitive ? value : value.toLowerCase(Locale.ROOT);
    }

    private String resolveTargetTenant(String requestedTenantId) {
        String current = SecurityContextHolder.getTenantId();
        if (current == null) {
            return StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : null;
        }
        if (StringUtils.hasText(requestedTenantId) && !current.equals(requestedTenantId.trim())) {
            throw new BizException(40310, "OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        }
        return current;
    }

    private String currentOperator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
