package com.triobase.service.auth.service;

import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.common.dto.authz.AuthzFieldRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserFieldAuthorizationAdapterTest {

    private final UserFieldAuthorizationAdapter adapter = new UserFieldAuthorizationAdapter();

    @Test
    void masksAdminPhonePolicyAtOwnerReadBoundary() {
        UserInfoPayload user = user("13812345678");

        adapter.applyRead(user, List.of(rule("phone", "MASKED", "EDITABLE", "LAST4")));

        assertThat(user.getPhone()).isEqualTo("*******5678");
    }

    @Test
    void removesHiddenPhoneAtOwnerReadBoundary() {
        UserInfoPayload user = user("13812345678");

        adapter.applyRead(user, List.of(rule("phone", "HIDDEN", "EDITABLE", null)));

        assertThat(user.getPhone()).isNull();
    }

    @Test
    void rejectsPhoneWriteWhenFrontendIsBypassed() {
        assertThatThrownBy(() -> adapter.validateWrite(
                Map.of("phone", "13900001111"),
                List.of(rule("phone", "VISIBLE", "DENIED", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phone");
    }

    private UserInfoPayload user(String phone) {
        UserInfoPayload user = new UserInfoPayload();
        user.setPhone(phone);
        return user;
    }

    private AuthzFieldRule rule(String key, String readMode, String writeMode, String strategy) {
        AuthzFieldRule rule = new AuthzFieldRule();
        rule.setFieldKey(key);
        rule.setReadMode(readMode);
        rule.setWriteMode(writeMode);
        rule.setMaskStrategy(strategy);
        return rule;
    }
}
