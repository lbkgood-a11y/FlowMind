package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.openapi.domain.entity.ApiProduct;
import com.triobase.service.openapi.domain.entity.ApiProductRouteMember;
import com.triobase.service.openapi.domain.entity.ApiProductVersion;
import com.triobase.service.openapi.domain.entity.ReleaseSnapshot;
import com.triobase.service.openapi.domain.entity.RouteDefinition;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ProductChangeClassification;
import com.triobase.service.openapi.domain.enums.ProductVisibility;
import com.triobase.service.openapi.domain.enums.RiskLevel;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateApiProductRequest;
import com.triobase.service.openapi.dto.ProductRouteMemberRequest;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductRouteMemberMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductAccessGrantMapper;
import com.triobase.service.openapi.infrastructure.mapper.ReleaseSnapshotMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteDefinitionMapper;
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
class ApiProductServiceTest {
 @Mock ApiProductMapper productMapper;@Mock ApiProductVersionMapper versionMapper;@Mock ApiProductRouteMemberMapper memberMapper;@Mock ReleaseSnapshotMapper releaseMapper;@Mock RouteDefinitionMapper routeMapper;@Mock AssetApprovalService approvals;@Mock IntegrationAuditService audit;@Mock ApiProductAccessGrantMapper grantMapper;ObjectMapper json=new ObjectMapper();ApiProductService service;
 @BeforeEach void setUp(){service=new ApiProductService(productMapper,versionMapper,memberMapper,releaseMapper,routeMapper,approvals,audit,json,grantMapper);SecurityContextHolder.set(new SecurityContextHolder.SecurityContext("user","owner","tenant-a",List.of(),List.of(),1L,1L,1L));}@AfterEach void clear(){SecurityContextHolder.clear();}
 @Test void createsTenantVisibleProductWithSemanticDraft(){when(productMapper.selectCount(any(Wrapper.class))).thenReturn(0L);var response=service.create(new CreateApiProductRequest(null,"orders","Orders","team-a",null,RiskLevel.MEDIUM,null,"docs","terms",json.createArrayNode().add("orders.read"),json.createObjectNode(),json.createObjectNode(),"1.0.0",ProductChangeClassification.MAJOR,null,List.of()));assertThat(response.visibility()).isEqualTo(ProductVisibility.TENANT);assertThat(response.semanticVersion()).isEqualTo("1.0.0");assertThat(response.lifecycleState()).isEqualTo(VersionLifecycleState.DRAFT);ArgumentCaptor<ApiProduct> product=ArgumentCaptor.forClass(ApiProduct.class);verify(productMapper).insert(product.capture());assertThat(product.getValue().getTenantId()).isEqualTo("tenant-a");}
 @Test void publicationPinsPublishedRouteReleaseAndContracts(){ApiProduct product=new ApiProduct();product.setId("product-1");product.setTenantId("tenant-a");product.setProductKey("orders");product.setDisplayName("Orders");product.setOwnerId("team-a");product.setRiskLevel(RiskLevel.MEDIUM);product.setVisibility(ProductVisibility.TENANT);ApiProductVersion version=new ApiProductVersion();version.setId("pv1");version.setApiProductId("product-1");version.setSemanticVersion("1.0.0");version.setMajorVersion(1);version.setMinorVersion(0);version.setPatchVersion(0);version.setLifecycleState(VersionLifecycleState.DRAFT);version.setChangeClassification(ProductChangeClassification.MAJOR);version.setScopes(json.createArrayNode().add("orders.read"));version.setTrafficPolicy(json.createObjectNode());version.setSecurityPolicy(json.createObjectNode());ApiProductRouteMember member=new ApiProductRouteMember();member.setApiProductVersionId("pv1");member.setRouteKey("orders.get");member.setReleaseSnapshotId("release-1");member.setOperations(json.createArrayNode().add("GET"));member.setScopes(json.createArrayNode().add("orders.read"));member.setCanonicalStructureVersionIds(json.createArrayNode().add("structure-v1"));ReleaseSnapshot release=new ReleaseSnapshot();release.setId("release-1");release.setEnvironment(Environment.DEV);release.setRouteDefinitionId("route-1");release.setLifecycleState(VersionLifecycleState.PUBLISHED);release.setSnapshotHash("hash");release.setPinnedDependencies(json.createObjectNode().set("requestMapping",json.createObjectNode().put("sourceStructureVersionId","structure-v1")));RouteDefinition route=new RouteDefinition();route.setId("route-1");route.setRouteKey("orders.get");when(versionMapper.selectById("pv1")).thenReturn(version);when(productMapper.selectById("product-1")).thenReturn(product);when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(member));when(releaseMapper.selectById("release-1")).thenReturn(release);when(routeMapper.selectById("route-1")).thenReturn(route);when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(null);var result=service.publish("pv1");assertThat(result.lifecycleState()).isEqualTo(VersionLifecycleState.PUBLISHED);assertThat(version.getPinnedRoutes()).hasSize(1);assertThat(version.getPinnedContracts()).contains(json.getNodeFactory().textNode("structure-v1"));}
 @Test void routeRemovalRequiresMajorVersion(){ApiProduct product=new ApiProduct();product.setId("product-1");product.setTenantId("tenant-a");product.setVisibility(ProductVisibility.TENANT);ApiProductVersion candidate=new ApiProductVersion();candidate.setId("pv2");candidate.setApiProductId("product-1");candidate.setSemanticVersion("1.1.0");candidate.setMajorVersion(1);candidate.setMinorVersion(1);candidate.setPatchVersion(0);candidate.setLifecycleState(VersionLifecycleState.DRAFT);candidate.setChangeClassification(ProductChangeClassification.MINOR);candidate.setScopes(json.createArrayNode());candidate.setTrafficPolicy(json.createObjectNode());candidate.setSecurityPolicy(json.createObjectNode());ApiProductVersion previous=new ApiProductVersion();previous.setId("pv1");previous.setApiProductId("product-1");previous.setMajorVersion(1);previous.setMinorVersion(0);previous.setPatchVersion(0);previous.setLifecycleState(VersionLifecycleState.PUBLISHED);ApiProductRouteMember current=member("orders.get","release-1");ApiProductRouteMember removed=member("orders.submit","release-2");ReleaseSnapshot release=new ReleaseSnapshot();release.setId("release-1");release.setEnvironment(Environment.DEV);release.setRouteDefinitionId("route-1");release.setLifecycleState(VersionLifecycleState.PUBLISHED);release.setSnapshotHash("hash");RouteDefinition route=new RouteDefinition();route.setRouteKey("orders.get");when(versionMapper.selectById("pv2")).thenReturn(candidate);when(productMapper.selectById("product-1")).thenReturn(product);when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(current),List.of(current,removed));when(releaseMapper.selectById("release-1")).thenReturn(release);when(routeMapper.selectById("route-1")).thenReturn(route);when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(previous);assertThatThrownBy(()->service.publish("pv2")).isInstanceOf(com.triobase.common.core.exception.BizException.class).hasMessage("OPENAPI_PRODUCT_CHANGE_CLASSIFICATION_TOO_WEAK");}
 private ApiProductRouteMember member(String key,String release){ApiProductRouteMember m=new ApiProductRouteMember();m.setRouteKey(key);m.setReleaseSnapshotId(release);m.setOperations(json.createArrayNode());m.setScopes(json.createArrayNode());m.setCanonicalStructureVersionIds(json.createArrayNode());return m;}
}
