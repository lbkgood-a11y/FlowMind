package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.ApiProduct;
import com.triobase.service.openapi.domain.entity.ApiProductRouteMember;
import com.triobase.service.openapi.domain.entity.ApiProductVersion;
import com.triobase.service.openapi.domain.entity.ApplicationClient;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ProductVisibility;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateSubscriptionRequest;
import com.triobase.service.openapi.dto.SubscriptionRouteOverrideRequest;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductAccessGrantMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductRouteMemberMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationClientMapper;
import com.triobase.service.openapi.infrastructure.mapper.ProductSubscriptionMapper;
import com.triobase.service.openapi.infrastructure.mapper.SubscriptionRouteOverrideMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class ProductSubscriptionServiceTest {
 @Mock ProductSubscriptionMapper subscriptionMapper;@Mock SubscriptionRouteOverrideMapper overrideMapper;@Mock ApplicationClientMapper clientMapper;@Mock ApiProductVersionMapper versionMapper;@Mock ApiProductMapper productMapper;@Mock ApiProductRouteMemberMapper memberMapper;@Mock ApiProductAccessGrantMapper grantMapper;@Mock AssetApprovalService approvals;@Mock IntegrationAuditService audit;@Mock PolicyChangeNotifier notifier;ObjectMapper json=new ObjectMapper();ProductSubscriptionService service;
 @BeforeEach void setUp(){service=new ProductSubscriptionService(subscriptionMapper,overrideMapper,clientMapper,versionMapper,productMapper,memberMapper,grantMapper,new PolicyRestrictionValidator(),approvals,audit,json,notifier);SecurityContextHolder.set(new SecurityContextHolder.SecurityContext("user","owner","tenant-a",List.of(),List.of(),1L,1L,1L));}@AfterEach void clear(){SecurityContextHolder.clear();}
 @Test void rejectsOperationOverrideNotPublishedByProduct(){ApplicationClient client=new ApplicationClient();client.setId("client-1");client.setApplicationId("app-1");client.setTenantId("tenant-a");client.setEnvironment(Environment.TEST);client.setLifecycleState(ApplicationLifecycleState.ACTIVE);ApiProductVersion version=new ApiProductVersion();version.setId("pv1");version.setApiProductId("product-1");version.setLifecycleState(VersionLifecycleState.PUBLISHED);version.setScopes(json.createArrayNode().add("orders.read"));version.setTrafficPolicy(json.createObjectNode().put("dailyQuota",1000));ApiProduct product=new ApiProduct();product.setId("product-1");product.setTenantId("tenant-a");product.setVisibility(ProductVisibility.TENANT);ApiProductRouteMember member=new ApiProductRouteMember();member.setRouteKey("orders.get");member.setOperations(json.createArrayNode().add("GET"));member.setScopes(json.createArrayNode().add("orders.read"));member.setCanonicalStructureVersionIds(json.createArrayNode().add("s1"));when(clientMapper.selectById("client-1")).thenReturn(client);when(versionMapper.selectById("pv1")).thenReturn(version);when(productMapper.selectById("product-1")).thenReturn(product);when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(member));var override=new SubscriptionRouteOverrideRequest("orders.get",false,json.createArrayNode().add("POST"),json.createArrayNode().add("orders.read"),json.createObjectNode().put("dailyQuota",100),json.createArrayNode(),json.createArrayNode().add("s1"),json.createObjectNode());assertThatThrownBy(()->service.request(new CreateSubscriptionRequest("client-1","pv1",json.createArrayNode().add("orders.read"),List.of(override),null,null))).isInstanceOf(BizException.class).hasMessage("OPENAPI_SUBSCRIPTION_OVERRIDE_BROADENS_OPERATION");}
}
