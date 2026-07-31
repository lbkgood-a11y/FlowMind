package com.triobase.service.apiruntime.service.authorization;

import com.triobase.common.dto.authz.AuthzFieldRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceContractFieldAuthorizationAdapterTest {

    private final ReferenceContractFieldAuthorizationAdapter adapter =
            new ReferenceContractFieldAuthorizationAdapter();

    @Test
    void filtersHiddenAndMaskedFieldsFromApiRead() {
        ReferenceContractDocument document = new ReferenceContractDocument();
        document.setId("C001");
        document.setAmount(new BigDecimal("12345678"));
        document.setOwnerUserId("U001");

        Map<String, Object> result = adapter.filterRead(document, List.of(
                rule("amount", "MASKED", "READ_ONLY", "LAST4"),
                rule("ownerUserId", "HIDDEN", "DENIED", null)));

        assertThat(result).containsEntry("amount", "****5678");
        assertThat(result).doesNotContainKey("ownerUserId");
    }

    @Test
    void rejectsDeniedWritesEvenWhenFrontendIsBypassed() {
        assertThatThrownBy(() -> adapter.validateWrite(
                Map.of("amount", new BigDecimal("99")),
                List.of(rule("amount", "VISIBLE", "DENIED", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void failsClosedWhenWriteRuleIsMissing() {
        assertThatThrownBy(() -> adapter.validateWrite(Map.of("amount", 99), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AuthzFieldRule rule(String fieldKey,
                                String readMode,
                                String writeMode,
                                String maskStrategy) {
        AuthzFieldRule rule = new AuthzFieldRule();
        rule.setFieldKey(fieldKey);
        rule.setReadMode(readMode);
        rule.setWriteMode(writeMode);
        rule.setMaskStrategy(maskStrategy);
        return rule;
    }
}
