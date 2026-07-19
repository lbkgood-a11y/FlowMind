package com.triobase.service.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.catalog.BusinessActionMetadata;
import com.triobase.common.dto.catalog.BusinessFieldMetadata;
import com.triobase.common.dto.catalog.BusinessObjectManifest;
import com.triobase.common.dto.catalog.BusinessObjectMetadata;
import com.triobase.common.dto.catalog.BusinessPageMetadata;
import com.triobase.common.dto.catalog.BusinessStatusMetadata;
import com.triobase.service.catalog.entity.BusinessObjectRecord;
import com.triobase.service.catalog.mapper.BusinessObjectRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BusinessCatalogService {

    public static final String GLOBAL_TENANT = "GLOBAL";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    private final BusinessObjectRecordMapper mapper;
    private final ObjectMapper objectMapper;

    public BusinessObjectMetadata sync(BusinessObjectManifest manifest) {
        validate(manifest);
        BusinessObjectRecord existing = mapper.selectOne(new LambdaQueryWrapper<BusinessObjectRecord>()
                .eq(BusinessObjectRecord::getTenantId, normalizeTenant(manifest.getTenantId()))
                .eq(BusinessObjectRecord::getObjectType, manifest.getObjectType().trim())
                .eq(BusinessObjectRecord::getVersion, manifest.getVersion() != null ? manifest.getVersion() : 1)
                .last("LIMIT 1"));
        BusinessObjectRecord record = existing != null ? existing : new BusinessObjectRecord();
        applyManifest(record, manifest);
        if (existing == null) {
            mapper.insert(record);
        } else {
            mapper.updateById(record);
        }
        return toMetadata(record);
    }

    public List<BusinessObjectMetadata> listEffective(String tenantId) {
        Map<String, BusinessObjectRecord> effective = new LinkedHashMap<>();
        latestByType(GLOBAL_TENANT).values().stream()
                .filter(record -> STATUS_PUBLISHED.equals(record.getLifecycleStatus()))
                .sorted(Comparator.comparing(BusinessObjectRecord::getDisplayName))
                .forEach(record -> effective.put(record.getObjectType(), record));

        String normalizedTenant = normalizeTenant(tenantId);
        if (!GLOBAL_TENANT.equals(normalizedTenant)) {
            for (BusinessObjectRecord tenantRecord : latestByType(normalizedTenant).values()) {
                if (STATUS_PUBLISHED.equals(tenantRecord.getLifecycleStatus())) {
                    effective.put(tenantRecord.getObjectType(), tenantRecord);
                } else if (STATUS_OFFLINE.equals(tenantRecord.getLifecycleStatus())) {
                    effective.remove(tenantRecord.getObjectType());
                }
            }
        }
        return effective.values().stream().map(this::toMetadata).toList();
    }

    public BusinessObjectMetadata getEffective(String objectType, String tenantId) {
        if (!StringUtils.hasText(objectType)) {
            throw new BizException(40091, "BUSINESS_OBJECT_TYPE_REQUIRED");
        }
        String normalizedTenant = normalizeTenant(tenantId);
        BusinessObjectRecord tenantRecord = GLOBAL_TENANT.equals(normalizedTenant)
                ? null
                : latestByType(normalizedTenant).get(objectType);
        if (tenantRecord != null && STATUS_OFFLINE.equals(tenantRecord.getLifecycleStatus())) {
            throw new BizException(40491, "BUSINESS_OBJECT_OFFLINE");
        }
        if (tenantRecord != null && STATUS_PUBLISHED.equals(tenantRecord.getLifecycleStatus())) {
            return toMetadata(tenantRecord);
        }
        BusinessObjectRecord global = latestByType(GLOBAL_TENANT).get(objectType);
        if (global != null && STATUS_PUBLISHED.equals(global.getLifecycleStatus())) {
            return toMetadata(global);
        }
        throw new BizException(40490, "BUSINESS_OBJECT_NOT_FOUND");
    }

    private Map<String, BusinessObjectRecord> latestByType(String tenantId) {
        List<BusinessObjectRecord> records = mapper.selectList(new LambdaQueryWrapper<BusinessObjectRecord>()
                .eq(BusinessObjectRecord::getTenantId, tenantId)
                .orderByAsc(BusinessObjectRecord::getVersion));
        Map<String, BusinessObjectRecord> latest = new LinkedHashMap<>();
        for (BusinessObjectRecord record : records) {
            latest.put(record.getObjectType(), record);
        }
        return latest;
    }

    private void validate(BusinessObjectManifest manifest) {
        if (manifest == null || !StringUtils.hasText(manifest.getObjectType())
                || !StringUtils.hasText(manifest.getDisplayName())
                || !StringUtils.hasText(manifest.getOwnerService())) {
            throw new BizException(40090, "BUSINESS_OBJECT_MANIFEST_INVALID");
        }
        if (manifest.getStatuses() == null || manifest.getStatuses().isEmpty()
                || manifest.getActions() == null || manifest.getActions().isEmpty()) {
            throw new BizException(40092, "BUSINESS_OBJECT_MANIFEST_METADATA_REQUIRED");
        }
    }

    private void applyManifest(BusinessObjectRecord record, BusinessObjectManifest manifest) {
        String tenantId = normalizeTenant(manifest.getTenantId());
        String status = StringUtils.hasText(manifest.getLifecycleStatus())
                ? manifest.getLifecycleStatus().trim()
                : STATUS_PUBLISHED;
        record.setTenantId(tenantId);
        record.setObjectType(manifest.getObjectType().trim());
        record.setDisplayName(manifest.getDisplayName().trim());
        record.setOwnerService(manifest.getOwnerService().trim());
        record.setDescription(manifest.getDescription());
        record.setVersion(manifest.getVersion() != null ? manifest.getVersion() : 1);
        record.setLifecycleStatus(status);
        record.setTenantOverride(!GLOBAL_TENANT.equals(tenantId));
        record.setPublishedAt(STATUS_PUBLISHED.equals(status) ? LocalDateTime.now() : record.getPublishedAt());
        record.setOfflineAt(STATUS_OFFLINE.equals(status) ? LocalDateTime.now() : null);
        record.setManifestJson(toJson(manifest));
        record.setStatusesJson(toJson(manifest.getStatuses()));
        record.setActionsJson(toJson(manifest.getActions()));
        record.setFieldsJson(toJson(manifest.getFields()));
        record.setPageJson(toJson(manifest.getPage()));
        record.setAttributesJson(toJson(manifest.getAttributes()));
    }

    private BusinessObjectMetadata toMetadata(BusinessObjectRecord record) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();
        metadata.setTenantId(record.getTenantId());
        metadata.setObjectType(record.getObjectType());
        metadata.setDisplayName(record.getDisplayName());
        metadata.setOwnerService(record.getOwnerService());
        metadata.setDescription(record.getDescription());
        metadata.setVersion(record.getVersion());
        metadata.setLifecycleStatus(record.getLifecycleStatus());
        metadata.setStatuses(fromJson(record.getStatusesJson(), new TypeReference<List<BusinessStatusMetadata>>() {}, new ArrayList<>()));
        metadata.setActions(fromJson(record.getActionsJson(), new TypeReference<List<BusinessActionMetadata>>() {}, new ArrayList<>()));
        metadata.setFields(fromJson(record.getFieldsJson(), new TypeReference<List<BusinessFieldMetadata>>() {}, new ArrayList<>()));
        metadata.setPage(fromJson(record.getPageJson(), new TypeReference<BusinessPageMetadata>() {}, new BusinessPageMetadata()));
        metadata.setAttributes(fromJson(record.getAttributesJson(), new TypeReference<Map<String, Object>>() {}, new LinkedHashMap<>()));
        return metadata;
    }

    private String currentTenantId() {
        return normalizeTenant(SecurityContextHolder.getTenantId());
    }

    public List<BusinessObjectMetadata> listEffectiveForCurrentTenant() {
        return listEffective(currentTenantId());
    }

    public BusinessObjectMetadata getEffectiveForCurrentTenant(String objectType) {
        return getEffective(objectType, currentTenantId());
    }

    private String normalizeTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : GLOBAL_TENANT;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException(50090, "BUSINESS_OBJECT_MANIFEST_SERIALIZE_FAILED");
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            return fallback;
        }
    }
}
