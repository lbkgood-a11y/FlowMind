package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.entity.TrafficPolicyVersion;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.PolicyScopeType;
import com.triobase.service.openapi.infrastructure.mapper.TrafficPolicyVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class TrafficPolicyServiceTest {
 @Mock TrafficPolicyVersionMapper mapper;@Mock IntegrationAuditService audit;@Mock PolicyChangeNotifier notifier;
 @Test void resolvesStricterLimitsAcrossHierarchy()throws Exception{ObjectMapper json=new ObjectMapper();TrafficPolicyVersion tenant=policy("tenant",PolicyScopeType.TENANT,"tenant-a",json.readTree("{\"ratePerSecond\":100,\"dailyQuota\":10000,\"requireTls\":true}"));TrafficPolicyVersion client=policy("client",PolicyScopeType.CLIENT,"client-1",json.readTree("{\"ratePerSecond\":20,\"dailyQuota\":2000}"));TrafficPolicyVersion route=policy("route",PolicyScopeType.ROUTE,"orders.submit",json.readTree("{\"dailyQuota\":500}"));when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(route,tenant,client));TrafficPolicyService service=new TrafficPolicyService(mapper,json,audit,notifier);var result=service.resolve("tenant-a",Environment.PROD,"client-1",null,"orders.submit","POST",null);assertThat(result.policy().path("ratePerSecond").asLong()).isEqualTo(20);assertThat(result.policy().path("dailyQuota").asLong()).isEqualTo(500);assertThat(result.policy().path("requireTls").asBoolean()).isTrue();assertThat(result.appliedPolicyVersionIds()).containsExactly("tenant","client","route");}
 private TrafficPolicyVersion policy(String id,PolicyScopeType type,String scope,com.fasterxml.jackson.databind.JsonNode content){TrafficPolicyVersion v=new TrafficPolicyVersion();v.setId(id);v.setScopeType(type);v.setScopeId(scope);v.setPolicyContent(content);return v;}
}
