package com.triobase.service.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.tenant.dto.CreateTenantRequest;
import com.triobase.service.tenant.dto.SaveTenantSettingRequest;
import com.triobase.service.tenant.dto.TenantResponse;
import com.triobase.service.tenant.dto.TenantSettingResponse;
import com.triobase.service.tenant.dto.TenantValidationResponse;
import com.triobase.service.tenant.dto.UpdateTenantRequest;
import com.triobase.service.tenant.dto.UpdateTenantStatusRequest;
import com.triobase.service.tenant.entity.SysTenant;
import com.triobase.service.tenant.entity.SysTenantSetting;
import com.triobase.service.tenant.mapper.TenantMapper;
import com.triobase.service.tenant.mapper.TenantSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final String DEFAULT_TENANT = "default";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String TYPE_STANDARD = "STANDARD";
    private static final String ISOLATION_SHARED_SCHEMA = "SHARED_SCHEMA";
    private static final String VALUE_TYPE_STRING = "STRING";
    private static final Set<String> TENANT_STATUSES = Set.of(STATUS_ACTIVE, STATUS_SUSPENDED, STATUS_DISABLED);
    private static final Set<String> TENANT_TYPES = Set.of("PLATFORM", TYPE_STANDARD, "TRIAL", "PARTNER");
    private static final Set<String> VALUE_TYPES = Set.of(VALUE_TYPE_STRING, "INTEGER", "BOOLEAN", "JSON");
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern SETTING_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9._:-]{1,127}$");

    private final TenantMapper tenantMapper;
    private final TenantSettingMapper settingMapper;
    private final ObjectMapper objectMapper;

    public PageResult<TenantResponse> pageTenants(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedStatus = normalizeStatus(status, false);
        String normalizedKeyword = normalizeBlank(keyword);
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<SysTenant>()
                .eq(!isPlatformAdmin(), SysTenant::getId, currentTenantId())
                .eq(normalizedStatus != null, SysTenant::getStatus, normalizedStatus)
                .and(normalizedKeyword != null, query -> query
                        .like(SysTenant::getId, normalizedKeyword)
                        .or()
                        .like(SysTenant::getTenantCode, normalizedKeyword)
                        .or()
                        .like(SysTenant::getTenantName, normalizedKeyword)
                        .or()
                        .like(SysTenant::getContactEmail, normalizedKeyword)
                        .or()
                        .like(SysTenant::getPlanCode, normalizedKeyword))
                .orderByAsc(SysTenant::getTenantCode);
        IPage<SysTenant> result = tenantMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(result.getRecords().stream()
                .map(TenantResponse::from)
                .toList(), result.getTotal(), safePage, safeSize);
    }

    public TenantResponse getTenant(String tenantId) {
        SysTenant tenant = findTenant(resolveReadableTenant(tenantId));
        return TenantResponse.from(tenant);
    }

    public TenantResponse currentTenant() {
        return TenantResponse.from(findTenant(currentTenantId()));
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (request == null) {
            throw new BizException(40060, "TENANT_REQUEST_REQUIRED");
        }
        String tenantId = normalizeTenantId(request.getTenantId());
        if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new BizException(40061, "TENANT_ID_INVALID");
        }
        if (!StringUtils.hasText(request.getTenantName())) {
            throw new BizException(40062, "TENANT_NAME_REQUIRED");
        }
        if (tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getId, tenantId)
                .or()
                .eq(SysTenant::getTenantCode, tenantId)) > 0) {
            throw new BizException(40063, "TENANT_ALREADY_EXISTS");
        }
        validateJson(request.getAttributesJson(), "TENANT_ATTRIBUTES_JSON_INVALID");

        SysTenant tenant = new SysTenant();
        tenant.setId(tenantId);
        tenant.setTenantCode(tenantId);
        tenant.setTenantName(request.getTenantName().trim());
        tenant.setShortName(normalizeBlank(request.getShortName()));
        tenant.setTenantType(normalizeTenantType(request.getTenantType()));
        tenant.setStatus(STATUS_ACTIVE);
        tenant.setIsolationMode(ISOLATION_SHARED_SCHEMA);
        tenant.setContactName(normalizeBlank(request.getContactName()));
        tenant.setContactEmail(normalizeBlank(request.getContactEmail()));
        tenant.setContactPhone(normalizeBlank(request.getContactPhone()));
        tenant.setRegion(normalizeBlank(request.getRegion()));
        tenant.setTimezone(defaultIfBlank(request.getTimezone(), "Asia/Shanghai"));
        tenant.setLocale(defaultIfBlank(request.getLocale(), "zh-CN"));
        tenant.setIndustry(normalizeBlank(request.getIndustry()));
        tenant.setPlanCode(defaultIfBlank(request.getPlanCode(), "BASIC").toUpperCase(Locale.ROOT));
        tenant.setMaxUsers(normalizeMaxUsers(request.getMaxUsers()));
        tenant.setExpireAt(request.getExpireAt());
        tenant.setAttributesJson(normalizeBlank(request.getAttributesJson()));
        tenantMapper.insert(tenant);
        return TenantResponse.from(tenant);
    }

    @Transactional
    public TenantResponse updateTenant(String tenantId, UpdateTenantRequest request) {
        if (request == null) {
            throw new BizException(40060, "TENANT_REQUEST_REQUIRED");
        }
        SysTenant tenant = findTenant(resolveWritableTenant(tenantId));
        validateJson(request.getAttributesJson(), "TENANT_ATTRIBUTES_JSON_INVALID");

        if (request.getTenantName() != null) {
            if (!StringUtils.hasText(request.getTenantName())) {
                throw new BizException(40062, "TENANT_NAME_REQUIRED");
            }
            tenant.setTenantName(request.getTenantName().trim());
        }
        applyCommonUpdates(tenant, request);
        tenantMapper.updateById(tenant);
        return TenantResponse.from(tenant);
    }

    @Transactional
    public TenantResponse updateStatus(String tenantId, UpdateTenantStatusRequest request) {
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new BizException(40064, "TENANT_STATUS_REQUIRED");
        }
        String normalizedStatus = normalizeStatus(request.getStatus(), true);
        SysTenant tenant = findTenant(resolveWritableTenant(tenantId));
        if (DEFAULT_TENANT.equals(tenant.getId()) && !STATUS_ACTIVE.equals(normalizedStatus)) {
            throw new BizException(40065, "DEFAULT_TENANT_CANNOT_BE_DISABLED");
        }
        tenant.setStatus(normalizedStatus);
        tenant.setSuspendedReason(STATUS_SUSPENDED.equals(normalizedStatus)
                ? normalizeBlank(request.getReason())
                : null);
        tenantMapper.updateById(tenant);
        return TenantResponse.from(tenant);
    }

    public List<TenantSettingResponse> listSettings(String tenantId) {
        String targetTenant = resolveReadableTenant(tenantId);
        findTenant(targetTenant);
        return settingMapper.selectList(new LambdaQueryWrapper<SysTenantSetting>()
                        .eq(SysTenantSetting::getTenantId, targetTenant)
                        .orderByAsc(SysTenantSetting::getSettingKey))
                .stream()
                .map(TenantSettingResponse::from)
                .toList();
    }

    @Transactional
    public TenantSettingResponse saveSetting(String tenantId, String settingKey, SaveTenantSettingRequest request) {
        if (request == null) {
            throw new BizException(40066, "TENANT_SETTING_REQUEST_REQUIRED");
        }
        String targetTenant = resolveWritableTenant(tenantId);
        findTenant(targetTenant);
        String normalizedKey = normalizeSettingKey(StringUtils.hasText(request.getSettingKey())
                ? request.getSettingKey()
                : settingKey);
        String valueType = normalizeValueType(request.getValueType());
        validateSettingValue(valueType, request.getSettingValue());

        SysTenantSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<SysTenantSetting>()
                .eq(SysTenantSetting::getTenantId, targetTenant)
                .eq(SysTenantSetting::getSettingKey, normalizedKey));
        boolean create = setting == null;
        if (setting == null) {
            setting = new SysTenantSetting();
            setting.setId(UlidGenerator.nextUlid());
            setting.setTenantId(targetTenant);
            setting.setSettingKey(normalizedKey);
        }
        setting.setSettingValue(request.getSettingValue());
        setting.setValueType(valueType);
        setting.setSensitiveFlag(Boolean.TRUE.equals(request.getSensitive()) ? (short) 1 : (short) 0);
        setting.setStatus(Boolean.FALSE.equals(request.getEnabled()) ? (short) 0 : (short) 1);
        setting.setDescription(normalizeBlank(request.getDescription()));
        if (create) {
            settingMapper.insert(setting);
        } else {
            settingMapper.updateById(setting);
        }
        return TenantSettingResponse.from(setting);
    }

    @Transactional
    public void deleteSetting(String tenantId, String settingKey) {
        String targetTenant = resolveWritableTenant(tenantId);
        findTenant(targetTenant);
        settingMapper.delete(new LambdaQueryWrapper<SysTenantSetting>()
                .eq(SysTenantSetting::getTenantId, targetTenant)
                .eq(SysTenantSetting::getSettingKey, normalizeSettingKey(settingKey)));
    }

    public TenantValidationResponse validateTenant(String tenantId) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        if (normalizedTenantId == null) {
            return TenantValidationResponse.missing(tenantId);
        }
        SysTenant tenant = tenantMapper.selectById(normalizedTenantId);
        if (tenant == null) {
            return TenantValidationResponse.missing(normalizedTenantId);
        }

        TenantValidationResponse response = new TenantValidationResponse();
        response.setTenantId(tenant.getId());
        response.setTenantName(tenant.getTenantName());
        response.setStatus(tenant.getStatus());
        response.setIsolationMode(tenant.getIsolationMode());
        response.setPlanCode(tenant.getPlanCode());
        response.setMaxUsers(tenant.getMaxUsers());
        response.setExpireAt(tenant.getExpireAt());

        if (!STATUS_ACTIVE.equals(tenant.getStatus())) {
            response.setActive(false);
            response.setInactiveReason("TENANT_" + tenant.getStatus());
            return response;
        }
        if (tenant.getExpireAt() != null && tenant.getExpireAt().isBefore(LocalDateTime.now())) {
            response.setActive(false);
            response.setInactiveReason("TENANT_EXPIRED");
            return response;
        }
        response.setActive(true);
        return response;
    }

    private void applyCommonUpdates(SysTenant tenant, UpdateTenantRequest request) {
        if (request.getShortName() != null) {
            tenant.setShortName(normalizeBlank(request.getShortName()));
        }
        if (request.getTenantType() != null) {
            tenant.setTenantType(normalizeTenantType(request.getTenantType()));
        }
        if (request.getContactName() != null) {
            tenant.setContactName(normalizeBlank(request.getContactName()));
        }
        if (request.getContactEmail() != null) {
            tenant.setContactEmail(normalizeBlank(request.getContactEmail()));
        }
        if (request.getContactPhone() != null) {
            tenant.setContactPhone(normalizeBlank(request.getContactPhone()));
        }
        if (request.getRegion() != null) {
            tenant.setRegion(normalizeBlank(request.getRegion()));
        }
        if (request.getTimezone() != null) {
            tenant.setTimezone(defaultIfBlank(request.getTimezone(), "Asia/Shanghai"));
        }
        if (request.getLocale() != null) {
            tenant.setLocale(defaultIfBlank(request.getLocale(), "zh-CN"));
        }
        if (request.getIndustry() != null) {
            tenant.setIndustry(normalizeBlank(request.getIndustry()));
        }
        if (request.getPlanCode() != null) {
            tenant.setPlanCode(defaultIfBlank(request.getPlanCode(), "BASIC").toUpperCase(Locale.ROOT));
        }
        if (request.getMaxUsers() != null) {
            tenant.setMaxUsers(normalizeMaxUsers(request.getMaxUsers()));
        }
        if (request.getExpireAt() != null) {
            tenant.setExpireAt(request.getExpireAt());
        }
        if (request.getAttributesJson() != null) {
            tenant.setAttributesJson(normalizeBlank(request.getAttributesJson()));
        }
    }

    private SysTenant findTenant(String tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException(40460, "TENANT_NOT_FOUND");
        }
        return tenant;
    }

    private String resolveReadableTenant(String requestedTenantId) {
        String normalized = normalizeTenantId(requestedTenantId);
        if (isPlatformAdmin()) {
            return normalized != null ? normalized : currentTenantId();
        }
        String currentTenantId = currentTenantId();
        if (normalized != null && !currentTenantId.equals(normalized)) {
            throw new BizException(40360, "TENANT_ACCESS_DENIED");
        }
        return currentTenantId;
    }

    private String resolveWritableTenant(String requestedTenantId) {
        return resolveReadableTenant(requestedTenantId);
    }

    private String currentTenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        return StringUtils.hasText(tenantId) ? normalizeTenantId(tenantId) : DEFAULT_TENANT;
    }

    private boolean isPlatformAdmin() {
        return SecurityContextHolder.getRoles().stream().anyMatch("ADMIN"::equals);
    }

    private String normalizeTenantId(String tenantId) {
        String normalized = normalizeBlank(tenantId);
        return normalized != null ? normalized.toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeTenantType(String tenantType) {
        String normalized = StringUtils.hasText(tenantType)
                ? tenantType.trim().toUpperCase(Locale.ROOT)
                : TYPE_STANDARD;
        if (!TENANT_TYPES.contains(normalized)) {
            throw new BizException(40067, "TENANT_TYPE_INVALID");
        }
        return normalized;
    }

    private String normalizeStatus(String status, boolean required) {
        String normalized = StringUtils.hasText(status)
                ? status.trim().toUpperCase(Locale.ROOT)
                : null;
        if (normalized == null) {
            if (required) {
                throw new BizException(40064, "TENANT_STATUS_REQUIRED");
            }
            return null;
        }
        if (!TENANT_STATUSES.contains(normalized)) {
            throw new BizException(40068, "TENANT_STATUS_INVALID");
        }
        return normalized;
    }

    private String normalizeValueType(String valueType) {
        String normalized = StringUtils.hasText(valueType)
                ? valueType.trim().toUpperCase(Locale.ROOT)
                : VALUE_TYPE_STRING;
        if (!VALUE_TYPES.contains(normalized)) {
            throw new BizException(40069, "TENANT_SETTING_TYPE_INVALID");
        }
        return normalized;
    }

    private String normalizeSettingKey(String settingKey) {
        String normalized = normalizeBlank(settingKey);
        if (normalized == null || !SETTING_KEY_PATTERN.matcher(normalized).matches()) {
            throw new BizException(40070, "TENANT_SETTING_KEY_INVALID");
        }
        return normalized;
    }

    private Integer normalizeMaxUsers(Integer maxUsers) {
        if (maxUsers == null) {
            return 100;
        }
        if (maxUsers < 1) {
            throw new BizException(40071, "TENANT_MAX_USERS_INVALID");
        }
        return maxUsers;
    }

    private void validateSettingValue(String valueType, String value) {
        if ("INTEGER".equals(valueType)) {
            try {
                Integer.parseInt(value);
            } catch (RuntimeException ex) {
                throw new BizException(40072, "TENANT_SETTING_INTEGER_INVALID");
            }
        } else if ("BOOLEAN".equals(valueType)
                && !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            throw new BizException(40073, "TENANT_SETTING_BOOLEAN_INVALID");
        } else if ("JSON".equals(valueType)) {
            validateJson(value, "TENANT_SETTING_JSON_INVALID");
        }
    }

    private void validateJson(String json, String errorMessage) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new BizException(40074, errorMessage);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalizeBlank(value);
        return normalized != null ? normalized : defaultValue;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
