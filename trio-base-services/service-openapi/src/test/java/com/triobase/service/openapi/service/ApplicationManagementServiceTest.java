package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.openapi.domain.entity.ApplicationClient;
import com.triobase.service.openapi.domain.entity.IntegrationApplication;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.RiskLevel;
import com.triobase.service.openapi.dto.CreateApplicationClientRequest;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationClientMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationContactMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationOwnerMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationApplicationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class ApplicationManagementServiceTest {
 @Mock IntegrationApplicationMapper appMapper;@Mock ApplicationOwnerMapper ownerMapper;@Mock ApplicationContactMapper contactMapper;@Mock ApplicationClientMapper clientMapper;@Mock AssetApprovalService approvals;@Mock IntegrationAuditService audit;@Mock PolicyChangeNotifier notifier;ApplicationManagementService service;
 @BeforeEach void setUp(){service=new ApplicationManagementService(appMapper,ownerMapper,contactMapper,clientMapper,approvals,audit,new ObjectMapper(),notifier);SecurityContextHolder.set(new SecurityContextHolder.SecurityContext("user","owner","tenant-a",List.of(),List.of(),1L,1L,1L));}@AfterEach void clear(){SecurityContextHolder.clear();}
 @Test void createsDistinctTestClientWithoutRouteGrant(){IntegrationApplication app=new IntegrationApplication();app.setId("app-1");app.setTenantId("tenant-a");app.setLifecycleState(ApplicationLifecycleState.ACTIVE);when(appMapper.selectById("app-1")).thenReturn(app);when(clientMapper.selectCount(any(Wrapper.class))).thenReturn(0L);var response=service.createClient("app-1",new CreateApplicationClientRequest(Environment.TEST,"erp-test",null,null,null));assertThat(response.environment()).isEqualTo(Environment.TEST);assertThat(response.lifecycleState()).isEqualTo(ApplicationLifecycleState.ACTIVE);ArgumentCaptor<ApplicationClient> client=ArgumentCaptor.forClass(ApplicationClient.class);verify(clientMapper).insert(client.capture());assertThat(client.getValue().getApplicationId()).isEqualTo("app-1");}
 @Test void suspensionImmediatelyChangesAdmissionState(){ApplicationClient client=new ApplicationClient();client.setId("client-1");client.setTenantId("tenant-a");client.setLifecycleState(ApplicationLifecycleState.ACTIVE);when(clientMapper.selectById("client-1")).thenReturn(client);var result=service.suspendClient("client-1","signature failures");assertThat(result.lifecycleState()).isEqualTo(ApplicationLifecycleState.SUSPENDED);assertThat(result.suspensionReason()).isEqualTo("signature failures");}
 @Test void suspendedClientCanBeExplicitlyReactivated(){ApplicationClient client=new ApplicationClient();client.setId("client-1");client.setApplicationId("app-1");client.setTenantId("tenant-a");client.setEnvironment(Environment.TEST);client.setLifecycleState(ApplicationLifecycleState.SUSPENDED);IntegrationApplication app=new IntegrationApplication();app.setId("app-1");app.setTenantId("tenant-a");app.setLifecycleState(ApplicationLifecycleState.ACTIVE);when(clientMapper.selectById("client-1")).thenReturn(client);when(appMapper.selectById("app-1")).thenReturn(app);var result=service.reactivateClient("client-1");assertThat(result.lifecycleState()).isEqualTo(ApplicationLifecycleState.ACTIVE);assertThat(result.suspensionReason()).isNull();}
 @Test void clientLookupDoesNotCrossTenantBoundary(){ApplicationClient client=new ApplicationClient();client.setId("client-b");client.setTenantId("tenant-b");when(clientMapper.selectById("client-b")).thenReturn(client);assertThatThrownBy(()->service.getClient("client-b")).isInstanceOf(com.triobase.common.core.exception.BizException.class).hasMessage("OPENAPI_APPLICATION_CLIENT_NOT_FOUND");}
}
