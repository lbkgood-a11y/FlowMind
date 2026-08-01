package com.triobase.service.auth.service;

import com.triobase.common.core.auth.FieldAuthorizationAdapter;
import com.triobase.common.core.auth.FieldEnforcementManifest;
import com.triobase.common.core.auth.FieldMaskHelper;
import com.triobase.common.core.auth.FieldRule;
import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.service.auth.dto.UserProfileResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class UserFieldAuthorizationAdapter implements FieldAuthorizationAdapter<UserInfoPayload> {

    public static final String RESOURCE_CODE = "USER";
    public static final Set<String> FIELD_KEYS = Set.of("username", "email", "phone", "status");

    @Override
    public FieldEnforcementManifest manifest() {
        return new FieldEnforcementManifest(
                "service-auth", RESOURCE_CODE, "BUSINESS_OBJECT", FIELD_KEYS,
                true, true, true, Set.of("LIST", "DETAIL", "PROFILE", "CREATE", "UPDATE"));
    }

    @Override
    public Map<String, Object> filterRead(UserInfoPayload source, List<? extends FieldRule> rules) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("username", source.getUsername());
        values.put("email", source.getEmail());
        values.put("phone", source.getPhone());
        values.put("status", source.getStatus());
        return FieldMaskHelper.applyReadRules(values, rules);
    }

    public UserInfoPayload applyRead(UserInfoPayload source, List<? extends FieldRule> rules) {
        Map<String, Object> values = filterRead(source, rules);
        source.setUsername((String) values.get("username"));
        source.setEmail((String) values.get("email"));
        source.setPhone((String) values.get("phone"));
        Object status = values.get("status");
        source.setStatus(status instanceof Number number ? number.intValue() : null);
        return source;
    }

    public UserProfileResponse applyRead(UserProfileResponse source, List<? extends FieldRule> rules) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("username", source.getUsername());
        values.put("email", source.getEmail());
        values.put("phone", source.getPhone());
        values = FieldMaskHelper.applyReadRules(values, rules);
        source.setUsername((String) values.get("username"));
        source.setEmail((String) values.get("email"));
        source.setPhone((String) values.get("phone"));
        return source;
    }

    @Override
    public void validateWrite(Map<String, Object> changes, List<? extends FieldRule> rules) {
        FieldMaskHelper.assertWritableFields(changes, rules);
    }
}
