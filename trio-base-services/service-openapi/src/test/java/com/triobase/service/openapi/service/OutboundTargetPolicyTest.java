package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundTargetPolicyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OutboundTargetPolicy policy = new OutboundTargetPolicy();

    @Test
    void rejectsLoopbackAndCloudMetadataRanges() {
        assertThatThrownBy(() -> policy.validate("http://127.0.0.1:8080", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("PRIVATE_OR_METADATA");
        assertThatThrownBy(() -> policy.validate("http://169.254.169.254/latest", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("PRIVATE_OR_METADATA");
    }

    @Test
    void rejectsUnapprovedHostBeforeInvocation() throws Exception {
        var networkPolicy = OBJECT_MAPPER.readTree("{\"allowedHosts\":[\"api.partner.example\"]}");
        assertThatThrownBy(() -> policy.validate("https://8.8.8.8", networkPolicy))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_CONNECTOR_HOST_NOT_APPROVED");
    }

    @Test
    void allowsApprovedPublicAddress() throws Exception {
        var networkPolicy = OBJECT_MAPPER.readTree("{\"allowedHosts\":[\"8.8.8.8\"]}");
        assertThatCode(() -> policy.validate("https://8.8.8.8", networkPolicy))
                .doesNotThrowAnyException();
    }
}
