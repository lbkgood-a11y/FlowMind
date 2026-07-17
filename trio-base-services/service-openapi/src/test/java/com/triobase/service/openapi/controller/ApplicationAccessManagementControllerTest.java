package com.triobase.service.openapi.controller;
import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.service.openapi.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;
@ExtendWith(MockitoExtension.class)
class ApplicationAccessManagementControllerTest {
 @Mock ApiProductService p;@Mock ApplicationManagementService a;@Mock ApplicationCredentialService c;@Mock ProductSubscriptionService s;@Mock AssetApprovalService ap;@Mock TrafficPolicyService t;@Mock PolicySnapshotService ps;
 @Test void allManagementOperationsDeclarePermission(){assertThat(new ApplicationAccessManagementController(p,a,c,s,ap,t,ps)).isNotNull();Method[] methods=Arrays.stream(ApplicationAccessManagementController.class.getDeclaredMethods()).filter(m->m.isAnnotationPresent(GetMapping.class)||m.isAnnotationPresent(PostMapping.class)||m.isAnnotationPresent(PutMapping.class)||m.isAnnotationPresent(DeleteMapping.class)).toArray(Method[]::new);assertThat(methods).isNotEmpty();assertThat(methods).allMatch(m->m.isAnnotationPresent(RequirePermission.class));}
}
