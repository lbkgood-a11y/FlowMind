package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessBusinessAuthorizationServiceTest {

    private final ProcessPackageMapper processPackageMapper = mock(ProcessPackageMapper.class);
    private final ProcessBusinessAuthorizationService service =
            new ProcessBusinessAuthorizationService(processPackageMapper, new ObjectMapper());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void allowsViewWhenBusinessPermissionIsPresent() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/expense-report/view"),
                null,
                null,
                null));
        when(processPackageMapper.selectById("PKG001")).thenReturn(packageWithViewPermission());

        service.requireCanView(instance());
    }

    @Test
    void rejectsViewWhenBusinessPermissionIsMissing() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of(),
                null,
                null,
                null));
        when(processPackageMapper.selectById("PKG001")).thenReturn(packageWithViewPermission());

        BizException exception = assertThrows(BizException.class,
                () -> service.requireCanView(instance()));

        assertEquals("BUSINESS_VIEW_PERMISSION_DENIED", exception.getMessage());
    }

    @Test
    void rejectsCrossTenantView() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_B",
                List.of(),
                List.of("/expense-report/view"),
                null,
                null,
                null));

        BizException exception = assertThrows(BizException.class,
                () -> service.requireCanView(instance()));

        assertEquals("PROCESS_INSTANCE_TENANT_DENIED", exception.getMessage());
    }

    private ProcessInstance instance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId("PI001");
        instance.setProcessPackageId("PKG001");
        instance.setTenantId("TENANT_A");
        instance.setBusinessType("expense_report");
        instance.setBusinessId("ER100");
        return instance;
    }

    private ProcessPackage packageWithViewPermission() {
        ProcessPackage pkg = new ProcessPackage();
        pkg.setId("PKG001");
        pkg.setPermissionPlanJson("""
                {
                  "resolvedPermissions": {
                    "view": {
                      "actionCode": "view",
                      "permissionCode": "/expense-report/view"
                    }
                  }
                }
                """);
        return pkg;
    }
}
