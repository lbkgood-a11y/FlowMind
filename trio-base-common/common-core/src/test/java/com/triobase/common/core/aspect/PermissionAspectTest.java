package com.triobase.common.core.aspect;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionAspectTest {

    private final PermissionAspect aspect = new PermissionAspect();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void allowsConcreteGrantedPermissionForCurrentRequestPath() throws Throwable {
        setRequest("GET", "/api/v1/forms/expense/instances");
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", List.of("/api/v1/forms/expense/instances:GET")));
        ProceedingJoinPoint joinPoint = mockJoinPoint();

        Object result = aspect.checkPermission(joinPoint, require("/api/v1/forms/*/instances:GET"));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void doesNotExpandConcreteFormPermissionToOtherFormKeys() throws Throwable {
        setRequest("GET", "/api/v1/forms/travel/instances");
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", List.of("/api/v1/forms/expense/instances:GET")));

        assertThatThrownBy(() -> aspect.checkPermission(mockJoinPoint(),
                require("/api/v1/forms/*/instances:GET")))
                .isInstanceOf(BizException.class);
    }

    @Test
    void allowsGrantedWildcardPermissionForConcreteRequestPath() throws Throwable {
        setRequest("PUT", "/api/v1/forms/travel/instances/INS001/process");
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", List.of("/api/v1/forms/*/instances/*/process:PUT")));
        ProceedingJoinPoint joinPoint = mockJoinPoint();

        Object result = aspect.checkPermission(joinPoint,
                require("/api/v1/forms/*/instances/*/process:PUT"));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void deniesPermissionBeforeAllowWhenDeniedPermissionMatchesCurrentRequest() {
        setRequest("GET", "/api/v1/roles");
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001",
                "alice",
                null,
                List.of(),
                List.of("/api/v1/**:GET"),
                List.of("/api/v1/roles:GET"),
                null,
                null,
                null,
                null,
                null,
                null));

        assertThatThrownBy(() -> aspect.checkPermission(mockJoinPoint(), require("/api/v1/**:GET")))
                .isInstanceOf(BizException.class);
    }

    private ProceedingJoinPoint mockJoinPoint() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
        return joinPoint;
    }

    private void setRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private RequirePermission require(String value) {
        return new RequirePermission() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RequirePermission.class;
            }
        };
    }
}
