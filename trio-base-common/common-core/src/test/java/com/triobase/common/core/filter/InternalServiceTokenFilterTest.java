package com.triobase.common.core.filter;

import com.triobase.common.core.config.InternalServiceSecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalServiceTokenFilterTest {

    private InternalServiceTokenFilter filter;

    @BeforeEach
    void setUp() {
        InternalServiceSecurityProperties properties = new InternalServiceSecurityProperties();
        properties.setToken("test-internal-token");
        properties.setAllowedCallers(List.of("service-workflow-engine"));
        filter = new InternalServiceTokenFilter(properties);
    }

    @Test
    void rejectsMissingCredentialsForInternalEndpoint() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsInvalidTokenForInternalEndpoint() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-workflow-engine");
        request.addHeader(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void acceptsConfiguredCallerAndToken() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-workflow-engine");
        request.addHeader(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, "test-internal-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/test");
        request.setRequestURI("/internal/v1/test");
        return request;
    }
}
