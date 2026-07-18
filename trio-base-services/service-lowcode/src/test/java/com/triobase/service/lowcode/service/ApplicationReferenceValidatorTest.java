package com.triobase.service.lowcode.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationReferenceValidatorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsRegisteredPermissionsAndPublishedWorkflowBinding() throws IOException {
        AtomicReference<String> authHeader = new AtomicReference<>();
        startServer("""
                {"code":0,"message":"success","data":[]}
                """, """
                {"code":0,"message":"success","data":true}
                """, authHeader);

        ApplicationReferenceValidator validator = validator(baseUrl());

        assertDoesNotThrow(() -> validator.validatePublication(version(), List.of(workflowAction())));
        assertEquals("service-lowcode", authHeader.get());
    }

    @Test
    void rejectsMissingPermissionRegistration() throws IOException {
        startServer("""
                {"code":0,"message":"success","data":["/api/v1/forms/*/submit:POST"]}
                """, """
                {"code":0,"message":"success","data":true}
                """, new AtomicReference<>());
        ApplicationReferenceValidator validator = validator(baseUrl());

        BizException exception = assertThrows(BizException.class,
                () -> validator.validatePublication(version(), List.of(workflowAction())));

        assertEquals("APPLICATION_PERMISSION_NOT_REGISTERED", exception.getMessage());
    }

    @Test
    void rejectsMissingPublishedWorkflowTarget() throws IOException {
        startServer("""
                {"code":0,"message":"success","data":[]}
                """, """
                {"code":0,"message":"success","data":false}
                """, new AtomicReference<>());
        ApplicationReferenceValidator validator = validator(baseUrl());

        BizException exception = assertThrows(BizException.class,
                () -> validator.validatePublication(version(), List.of(workflowAction())));

        assertEquals("APPLICATION_PROCESS_BINDING_NOT_FOUND", exception.getMessage());
    }

    @Test
    void rejectsActionWithoutPermissionCode() throws IOException {
        startServer("""
                {"code":0,"message":"success","data":[]}
                """, """
                {"code":0,"message":"success","data":true}
                """, new AtomicReference<>());
        ApplicationActionRequest action = workflowAction();
        action.setPermissionCode(null);
        ApplicationReferenceValidator validator = validator(baseUrl());

        BizException exception = assertThrows(BizException.class,
                () -> validator.validatePublication(version(), List.of(action)));

        assertEquals("APPLICATION_ACTION_PERMISSION_REQUIRED", exception.getMessage());
    }

    private void startServer(String permissionsBody,
                             String workflowBody,
                             AtomicReference<String> authHeader) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/permissions/missing", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst(InternalServiceTokenFilter.HEADER_SERVICE_NAME));
            respond(exchange, permissionsBody);
        });
        server.createContext("/internal/v1/process-packages/published/exists",
                exchange -> respond(exchange, workflowBody));
        server.start();
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private ApplicationReferenceValidator validator(String baseUrl) {
        InternalServiceSecurityProperties properties = new InternalServiceSecurityProperties();
        properties.setToken("test-token");
        return new ApplicationReferenceValidator(properties, baseUrl, baseUrl);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private LcApplicationVersion version() {
        LcApplicationVersion version = new LcApplicationVersion();
        version.setViewPermissionCode("/api/v1/forms/expense/instances:GET");
        return version;
    }

    private ApplicationActionRequest workflowAction() {
        ApplicationActionRequest action = new ApplicationActionRequest();
        action.setActionCode("submitAndLaunch");
        action.setActionType("SUBMIT_AND_LAUNCH_WORKFLOW");
        action.setLabel("Submit");
        action.setPermissionCode("/api/v1/forms/*/submit:POST");
        action.setProcessKey("expense_report");
        action.setMetadataJson("{}");
        return action;
    }
}
