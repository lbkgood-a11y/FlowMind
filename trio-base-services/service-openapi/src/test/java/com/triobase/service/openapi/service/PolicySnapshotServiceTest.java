package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.openapi.domain.entity.PolicySnapshot;
import com.triobase.service.openapi.domain.entity.TrafficPolicyVersion;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.PolicyScopeType;
import com.triobase.service.openapi.infrastructure.mapper.PolicyEnforcementStateMapper;
import com.triobase.service.openapi.infrastructure.mapper.PolicySnapshotMapper;
import com.triobase.service.openapi.infrastructure.mapper.TrafficPolicyVersionMapper;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class PolicySnapshotServiceTest {
 @Mock PolicySnapshotMapper snapshotMapper;@Mock TrafficPolicyVersionMapper policyMapper;@Mock PolicyEnforcementStateMapper enforcementMapper;@Mock PolicySnapshotSigner signer;@Mock PolicySnapshotPublisher publisher;@Mock IntegrationAuditService audit;PolicySnapshotService service;ObjectMapper json=new ObjectMapper();
 @BeforeEach void setUp(){service=new PolicySnapshotService(snapshotMapper,policyMapper,enforcementMapper,signer,publisher,json,audit);SecurityContextHolder.set(new SecurityContextHolder.SecurityContext("user","owner","tenant-a",List.of(),List.of(),1L,1L,1L));}@AfterEach void clear(){SecurityContextHolder.clear();}
 @Test void publishesSignedImmutableSnapshotAndMarksBothEnforcementPointsLagging(){TrafficPolicyVersion p=new TrafficPolicyVersion();p.setId("policy-1");p.setTenantId("tenant-a");p.setScopeType(PolicyScopeType.CLIENT);p.setScopeId("client-1");p.setPolicyVersion(1L);p.setContentHash("content-hash");p.setPolicyContent(json.createObjectNode().put("ratePerSecond",10));when(snapshotMapper.nextVersion("tenant-a","PROD")).thenReturn(4L);when(policyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(p));when(signer.sign(any(),any(),any(Long.class),any())).thenReturn("signature");when(publisher.publish(any())).thenReturn(true);var result=service.publish(null,Environment.PROD);assertThat(result.getSnapshotVersion()).isEqualTo(4L);assertThat(result.getSignature()).isEqualTo("signature");assertThat(result.getPolicyContent().path("policies")).hasSize(1);verify(enforcementMapper,times(2)).requireVersion(any(),any(),any(),any());ArgumentCaptor<PolicySnapshot> captured=ArgumentCaptor.forClass(PolicySnapshot.class);verify(snapshotMapper).insert(captured.capture());assertThat(captured.getValue().getContentHash()).hasSize(64);}
 @Test void laggingEnforcementFailsClosed(){com.triobase.service.openapi.domain.entity.PolicyEnforcementState state=new com.triobase.service.openapi.domain.entity.PolicyEnforcementState();state.setRequiredPolicyVersion(5L);state.setAppliedPolicyVersion(4L);state.setDriftState("LAGGING");when(enforcementMapper.find("platform-gateway","tenant-a","PROD")).thenReturn(state);assertThatThrownBy(()->service.requireCurrent("platform-gateway",null,Environment.PROD)).isInstanceOf(com.triobase.common.core.exception.BizException.class).hasMessage("OPENAPI_POLICY_ENFORCEMENT_LAG_FAIL_CLOSED");}
}
