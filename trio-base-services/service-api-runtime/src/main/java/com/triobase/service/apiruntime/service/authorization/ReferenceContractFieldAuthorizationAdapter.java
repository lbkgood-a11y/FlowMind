package com.triobase.service.apiruntime.service.authorization;

import com.triobase.common.core.auth.FieldAuthorizationAdapter;
import com.triobase.common.core.auth.FieldEnforcementManifest;
import com.triobase.common.core.auth.FieldMaskHelper;
import com.triobase.common.core.auth.FieldRule;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReferenceContractFieldAuthorizationAdapter
        implements FieldAuthorizationAdapter<ReferenceContractDocument> {

    @Override
    public FieldEnforcementManifest manifest() {
        return new FieldEnforcementManifest(
                "service-api-runtime", "CUSTOM_DOC:CONTRACT", "CUSTOM_DOC",
                Set.of("amount", "customerName", "paymentTerms"),
                true, true, true,
                Set.of("LIST", "DETAIL", "CREATE", "UPDATE", "OWNER_ACTION"));
    }

    @Override
    public Map<String, Object> filterRead(ReferenceContractDocument source,
                                          List<? extends FieldRule> rules) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", source.getId());
        values.put("status", source.getStatus());
        values.put("amount", source.getAmount());
        values.put("ownerUserId", source.getOwnerUserId());
        values.put("ownerOrgId", source.getOwnerOrgId());
        values.put("submittedBy", source.getSubmittedBy());
        return FieldMaskHelper.applyReadRules(values, rules);
    }

    @Override
    public void validateWrite(Map<String, Object> changes,
                              List<? extends FieldRule> rules) {
        FieldMaskHelper.assertWritableFields(changes, rules);
    }
}
