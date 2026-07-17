package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ApiProduct;
import com.triobase.service.openapi.domain.entity.ApiProductRouteMember;
import com.triobase.service.openapi.domain.entity.ApiProductVersion;
import com.triobase.service.openapi.domain.entity.ApiProductAccessGrant;
import com.triobase.service.openapi.domain.entity.ReleaseSnapshot;
import com.triobase.service.openapi.domain.entity.RouteDefinition;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ProductChangeClassification;
import com.triobase.service.openapi.domain.enums.ProductVisibility;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.ApiProductVersionMutationRequest;
import com.triobase.service.openapi.dto.ApiProductVersionResponse;
import com.triobase.service.openapi.dto.CreateApiProductRequest;
import com.triobase.service.openapi.dto.ProductRouteMemberRequest;
import com.triobase.service.openapi.dto.UpdateApiProductRequest;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductRouteMemberMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductAccessGrantMapper;
import com.triobase.service.openapi.infrastructure.mapper.ReleaseSnapshotMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ApiProductService {
    private static final Pattern SEMVER=Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
    private final ApiProductMapper productMapper; private final ApiProductVersionMapper versionMapper;
    private final ApiProductRouteMemberMapper memberMapper; private final ReleaseSnapshotMapper releaseMapper;
    private final RouteDefinitionMapper routeMapper; private final AssetApprovalService approvalService;
    private final IntegrationAuditService auditService; private final ObjectMapper objectMapper; private final ApiProductAccessGrantMapper grantMapper;

    @Transactional
    public ApiProductVersionResponse create(CreateApiProductRequest request) {
        validateCreate(request); String tenant=targetTenant(request.tenantId());
        if(productMapper.selectCount(new LambdaQueryWrapper<ApiProduct>().eq(ApiProduct::getProductKey,request.productKey().trim())
                .eq(tenant!=null,ApiProduct::getTenantId,tenant).isNull(tenant==null,ApiProduct::getTenantId))>0)
            throw new BizException(40960,"OPENAPI_PRODUCT_ALREADY_EXISTS");
        ApiProduct product=new ApiProduct(); product.setId(UlidGenerator.nextUlid()); product.setTenantId(tenant);
        product.setProductKey(request.productKey().trim()); product.setDisplayName(request.displayName().trim());
        product.setOwnerId(request.ownerId().trim()); product.setAudience(request.audience()); product.setRiskLevel(request.riskLevel());
        product.setVisibility(request.visibility()==null?ProductVisibility.TENANT:request.visibility());
        product.setDocumentation(request.documentation()); product.setTerms(request.terms());
        product.setDefaultScopes(copyArray(request.defaultScopes())); product.setDefaultTrafficPolicy(copyObject(request.defaultTrafficPolicy()));
        product.setDefaultSecurityPolicy(copyObject(request.defaultSecurityPolicy())); product.setLifecycleState(AssetLifecycleState.ACTIVE);
        initialize(product); productMapper.insert(product);
        ParsedVersion parsed=parse(request.semanticVersion()); ApiProductVersion version=new ApiProductVersion();
        version.setId(UlidGenerator.nextUlid()); version.setApiProductId(product.getId()); applyVersion(version,parsed,
                request.documentation(),request.terms(),request.defaultScopes(),request.defaultTrafficPolicy(),request.defaultSecurityPolicy(),
                request.changeClassification(),request.migrationNotice()); version.setLifecycleState(VersionLifecycleState.DRAFT); initialize(version);
        versionMapper.insert(version); replaceMembers(version.getId(),request.routes());
        auditService.success("API_PRODUCT_CREATED","API_PRODUCT",product.getId(),JsonNodeFactory.instance.objectNode().put("versionId",version.getId()));
        return response(product,version);
    }

    @Transactional
    public ApiProduct updateProduct(String productId, UpdateApiProductRequest request){
        ApiProduct product=requireProduct(productId); if(request==null||!StringUtils.hasText(request.displayName())||!StringUtils.hasText(request.ownerId())||request.riskLevel()==null)
            throw new BizException(40060,"OPENAPI_PRODUCT_REQUEST_INVALID");
        product.setDisplayName(request.displayName().trim()); product.setOwnerId(request.ownerId().trim()); product.setAudience(request.audience());
        product.setRiskLevel(request.riskLevel()); product.setVisibility(request.visibility()==null?ProductVisibility.TENANT:request.visibility());
        product.setDocumentation(request.documentation()); product.setTerms(request.terms()); product.setDefaultScopes(copyArray(request.defaultScopes()));
        product.setDefaultTrafficPolicy(copyObject(request.defaultTrafficPolicy())); product.setDefaultSecurityPolicy(copyObject(request.defaultSecurityPolicy()));
        touch(product); productMapper.updateById(product); auditService.success("API_PRODUCT_UPDATED","API_PRODUCT",productId,JsonNodeFactory.instance.objectNode()); return product;
    }

    @Transactional
    public ApiProductVersionResponse createDraft(String productId, ApiProductVersionMutationRequest request){
        ApiProduct product=requireProduct(productId); validateMutation(request);
        if(versionMapper.selectCount(new LambdaQueryWrapper<ApiProductVersion>().eq(ApiProductVersion::getApiProductId,productId)
                .eq(ApiProductVersion::getLifecycleState,VersionLifecycleState.DRAFT))>0) throw new BizException(40960,"OPENAPI_PRODUCT_DRAFT_EXISTS");
        ParsedVersion parsed=parse(request.semanticVersion()); ApiProductVersion version=new ApiProductVersion(); version.setId(UlidGenerator.nextUlid());
        version.setApiProductId(productId); applyVersion(version,parsed,request.documentation(),request.terms(),request.scopes(),request.trafficPolicy(),
                request.securityPolicy(),request.changeClassification(),request.migrationNotice()); version.setLifecycleState(VersionLifecycleState.DRAFT);
        initialize(version); versionMapper.insert(version); replaceMembers(version.getId(),request.routes()); return response(product,version);
    }

    @Transactional
    public ApiProductVersionResponse updateDraft(String versionId, ApiProductVersionMutationRequest request){
        validateMutation(request); ApiProductVersion version=requireVersion(versionId); ApiProduct product=requireProduct(version.getApiProductId()); requireDraft(version);
        ParsedVersion parsed=parse(request.semanticVersion()); applyVersion(version,parsed,request.documentation(),request.terms(),request.scopes(),request.trafficPolicy(),
                request.securityPolicy(),request.changeClassification(),request.migrationNotice()); touch(version); versionMapper.updateById(version);
        replaceMembers(versionId,request.routes()); return response(product,version);
    }

    @Transactional
    public ApiProductVersionResponse publish(String versionId){
        ApiProductVersion version=requireVersion(versionId); ApiProduct product=requireProduct(version.getApiProductId()); requireDraft(version);
        List<ApiProductRouteMember> members=members(versionId); if(members.isEmpty()) throw new BizException(40961,"OPENAPI_PRODUCT_REQUIRES_ROUTE");
        boolean production=false; ArrayNode pinnedRoutes=objectMapper.createArrayNode(); ArrayNode contracts=objectMapper.createArrayNode();
        for(ApiProductRouteMember member:members){ ReleaseSnapshot release=releaseMapper.selectById(member.getReleaseSnapshotId());
            if(release==null||release.getLifecycleState()!=VersionLifecycleState.PUBLISHED) throw new BizException(40961,"OPENAPI_PRODUCT_ROUTE_RELEASE_INVALID");
            RouteDefinition route=routeMapper.selectById(release.getRouteDefinitionId()); if(route==null||!member.getRouteKey().equals(route.getRouteKey()))
                throw new BizException(40961,"OPENAPI_PRODUCT_ROUTE_KEY_MISMATCH");
            production|=release.getEnvironment()==Environment.PROD;
            pinnedRoutes.add(objectMapper.createObjectNode().put("routeKey",member.getRouteKey()).put("releaseSnapshotId",release.getId()).put("snapshotHash",release.getSnapshotHash()));
            JsonNode dependencies=release.getPinnedDependencies(); if(dependencies!=null){ addContract(contracts,dependencies.at("/requestMapping/sourceStructureVersionId")); addContract(contracts,dependencies.at("/requestMapping/targetStructureVersionId")); addContract(contracts,dependencies.at("/responseMapping/sourceStructureVersionId")); addContract(contracts,dependencies.at("/responseMapping/targetStructureVersionId")); }
        }
        ProductChangeClassification required=requiredChange(version,members); if(rank(version.getChangeClassification())<rank(required))
            throw new BizException(40962,"OPENAPI_PRODUCT_CHANGE_CLASSIFICATION_TOO_WEAK");
        validateIncrement(version,required);
        Set<String> roles=new HashSet<>(); if(production){roles.add("ASSET_OWNER");roles.add("PLATFORM_ADMIN");}
        if(product.getVisibility()==ProductVisibility.PLATFORM_PUBLIC)roles.add("PLATFORM_ADMIN");
        if(!roles.isEmpty())approvalService.requireApproved("API_PRODUCT_VERSION",versionId,roles);
        version.setPinnedRoutes(pinnedRoutes); version.setPinnedContracts(contracts); version.setValidationResult(objectMapper.createObjectNode().put("valid",true).put("requiredChange",required.name()));
        version.setLifecycleState(VersionLifecycleState.PUBLISHED); version.setPublishedBy(operator()); version.setPublishedAt(LocalDateTime.now()); touch(version);
        versionMapper.updateById(version); auditService.success("API_PRODUCT_VERSION_PUBLISHED","API_PRODUCT_VERSION",versionId,version.getValidationResult());
        return response(product,version);
    }

    public ApiProductVersionResponse getVersion(String versionId){ApiProductVersion v=requireVersion(versionId);return response(requireProduct(v.getApiProductId()),v);}
    @Transactional public ApiProductVersionResponse deprecate(String id){ApiProductVersion v=requireVersion(id);ApiProduct p=requireProduct(v.getApiProductId());if(v.getLifecycleState()!=VersionLifecycleState.PUBLISHED)throw new BizException(40960,"OPENAPI_PRODUCT_VERSION_NOT_PUBLISHED");v.setLifecycleState(VersionLifecycleState.DEPRECATED);touch(v);versionMapper.updateById(v);return response(p,v);}
    @Transactional public ApiProductVersionResponse archiveVersion(String id){ApiProductVersion v=requireVersion(id);ApiProduct p=requireProduct(v.getApiProductId());if(v.getLifecycleState()==VersionLifecycleState.PUBLISHED)throw new BizException(40960,"OPENAPI_PRODUCT_VERSION_MUST_BE_DEPRECATED");v.setLifecycleState(VersionLifecycleState.ARCHIVED);touch(v);versionMapper.updateById(v);return response(p,v);}
    @Transactional public void archiveProduct(String id){ApiProduct p=requireProduct(id);if(versionMapper.selectCount(new LambdaQueryWrapper<ApiProductVersion>().eq(ApiProductVersion::getApiProductId,id).in(ApiProductVersion::getLifecycleState,VersionLifecycleState.DRAFT,VersionLifecycleState.PUBLISHED))>0)throw new BizException(40960,"OPENAPI_PRODUCT_HAS_ACTIVE_VERSIONS");p.setLifecycleState(AssetLifecycleState.ARCHIVED);touch(p);productMapper.updateById(p);auditService.success("API_PRODUCT_ARCHIVED","API_PRODUCT",id,JsonNodeFactory.instance.objectNode());}
    @Transactional public ApiProductAccessGrant grantPrivateAccess(String productId,String applicationId){ApiProduct p=requireProduct(productId);if(p.getVisibility()!=ProductVisibility.PRIVATE||!StringUtils.hasText(applicationId))throw new BizException(40060,"OPENAPI_PRIVATE_PRODUCT_GRANT_INVALID");ApiProductAccessGrant g=new ApiProductAccessGrant();g.setId(UlidGenerator.nextUlid());g.setApiProductId(productId);g.setGranteeType("APPLICATION");g.setGranteeId(applicationId);initialize(g);grantMapper.insert(g);return g;}
    public List<ApiProduct> discover(String applicationId){String tenant=SecurityContextHolder.getTenantId();if(!StringUtils.hasText(tenant)||!StringUtils.hasText(applicationId))throw new BizException(40060,"OPENAPI_PRODUCT_DISCOVERY_INVALID");return productMapper.discover(tenant,applicationId);}

    private ProductChangeClassification requiredChange(ApiProductVersion candidate,List<ApiProductRouteMember> current){
        ApiProductVersion previous=versionMapper.selectOne(new LambdaQueryWrapper<ApiProductVersion>().eq(ApiProductVersion::getApiProductId,candidate.getApiProductId())
                .eq(ApiProductVersion::getLifecycleState,VersionLifecycleState.PUBLISHED).orderByDesc(ApiProductVersion::getMajorVersion)
                .orderByDesc(ApiProductVersion::getMinorVersion).orderByDesc(ApiProductVersion::getPatchVersion).last("LIMIT 1"));
        if(previous==null)return candidate.getChangeClassification(); List<ApiProductRouteMember> prior=members(previous.getId());
        Map<String,String> oldRoutes=prior.stream().collect(Collectors.toMap(ApiProductRouteMember::getRouteKey,ApiProductRouteMember::getReleaseSnapshotId));
        Map<String,String> newRoutes=current.stream().collect(Collectors.toMap(ApiProductRouteMember::getRouteKey,ApiProductRouteMember::getReleaseSnapshotId));
        if(!newRoutes.keySet().containsAll(oldRoutes.keySet()))return ProductChangeClassification.MAJOR;
        if(oldRoutes.entrySet().stream().anyMatch(e->!e.getValue().equals(newRoutes.get(e.getKey()))))return ProductChangeClassification.MAJOR;
        return newRoutes.size()>oldRoutes.size()?ProductChangeClassification.MINOR:ProductChangeClassification.PATCH;
    }
    private void validateIncrement(ApiProductVersion c,ProductChangeClassification required){ApiProductVersion p=versionMapper.selectOne(new LambdaQueryWrapper<ApiProductVersion>().eq(ApiProductVersion::getApiProductId,c.getApiProductId()).eq(ApiProductVersion::getLifecycleState,VersionLifecycleState.PUBLISHED).orderByDesc(ApiProductVersion::getMajorVersion).orderByDesc(ApiProductVersion::getMinorVersion).orderByDesc(ApiProductVersion::getPatchVersion).last("LIMIT 1"));if(p==null)return;boolean valid=switch(required){case MAJOR->c.getMajorVersion()>p.getMajorVersion();case MINOR->c.getMajorVersion().equals(p.getMajorVersion())&&c.getMinorVersion()>p.getMinorVersion();case PATCH->c.getMajorVersion().equals(p.getMajorVersion())&&c.getMinorVersion().equals(p.getMinorVersion())&&c.getPatchVersion()>p.getPatchVersion();};if(!valid)throw new BizException(40962,"OPENAPI_PRODUCT_SEMANTIC_VERSION_INCREMENT_INVALID");}
    private int rank(ProductChangeClassification c){return switch(c){case PATCH->1;case MINOR->2;case MAJOR->3;};}
    private void addContract(ArrayNode contracts,JsonNode value){if(value!=null&&!value.isMissingNode()&&!value.asText().isBlank()&&java.util.stream.StreamSupport.stream(contracts.spliterator(),false).noneMatch(n->n.asText().equals(value.asText())))contracts.add(value.asText());}
    private void replaceMembers(String versionId,List<ProductRouteMemberRequest> requests){memberMapper.delete(new LambdaQueryWrapper<ApiProductRouteMember>().eq(ApiProductRouteMember::getApiProductVersionId,versionId));if(requests==null)return;Set<String> keys=new HashSet<>();for(ProductRouteMemberRequest r:requests){if(r==null||!StringUtils.hasText(r.routeKey())||!StringUtils.hasText(r.releaseSnapshotId())||!keys.add(r.routeKey()))throw new BizException(40060,"OPENAPI_PRODUCT_ROUTE_INVALID");ApiProductRouteMember m=new ApiProductRouteMember();m.setId(UlidGenerator.nextUlid());m.setApiProductVersionId(versionId);m.setRouteKey(r.routeKey());m.setReleaseSnapshotId(r.releaseSnapshotId());m.setOperations(copyArray(r.operations()));m.setScopes(copyArray(r.scopes()));m.setCanonicalStructureVersionIds(copyArray(r.canonicalStructureVersionIds()));initialize(m);memberMapper.insert(m);}}
    private List<ApiProductRouteMember> members(String id){return memberMapper.selectList(new LambdaQueryWrapper<ApiProductRouteMember>().eq(ApiProductRouteMember::getApiProductVersionId,id).orderByAsc(ApiProductRouteMember::getRouteKey));}
    private ApiProductVersionResponse response(ApiProduct p,ApiProductVersion v){List<ProductRouteMemberRequest> routes=members(v.getId()).stream().map(m->new ProductRouteMemberRequest(m.getRouteKey(),m.getReleaseSnapshotId(),m.getOperations(),m.getScopes(),m.getCanonicalStructureVersionIds())).toList();return new ApiProductVersionResponse(p.getId(),v.getId(),p.getTenantId(),p.getProductKey(),p.getDisplayName(),p.getOwnerId(),p.getRiskLevel(),p.getVisibility(),v.getSemanticVersion(),v.getLifecycleState(),v.getChangeClassification(),v.getDocumentation(),v.getTerms(),v.getScopes(),v.getTrafficPolicy(),v.getSecurityPolicy(),v.getMigrationNotice(),routes);}
    private void applyVersion(ApiProductVersion v,ParsedVersion p,String docs,String terms,JsonNode scopes,JsonNode traffic,JsonNode security,ProductChangeClassification change,String notice){v.setSemanticVersion(p.text());v.setMajorVersion(p.major());v.setMinorVersion(p.minor());v.setPatchVersion(p.patch());v.setDocumentation(docs);v.setTerms(terms);v.setScopes(copyArray(scopes));v.setTrafficPolicy(copyObject(traffic));v.setSecurityPolicy(copyObject(security));v.setChangeClassification(change);v.setMigrationNotice(notice);v.setPinnedRoutes(objectMapper.createArrayNode());v.setPinnedContracts(objectMapper.createArrayNode());v.setValidationResult(objectMapper.createObjectNode());}
    private ParsedVersion parse(String value){Matcher m=SEMVER.matcher(value==null?"":value);if(!m.matches())throw new BizException(40060,"OPENAPI_PRODUCT_SEMVER_INVALID");return new ParsedVersion(value,Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));}
    private void validateCreate(CreateApiProductRequest r){if(r==null||!StringUtils.hasText(r.productKey())||!StringUtils.hasText(r.displayName())||!StringUtils.hasText(r.ownerId())||r.riskLevel()==null||r.changeClassification()==null)throw new BizException(40060,"OPENAPI_PRODUCT_REQUEST_INVALID");parse(r.semanticVersion());}
    private void validateMutation(ApiProductVersionMutationRequest r){if(r==null||r.changeClassification()==null)throw new BizException(40060,"OPENAPI_PRODUCT_REQUEST_INVALID");parse(r.semanticVersion());}
    private void requireDraft(ApiProductVersion v){if(v.getLifecycleState()!=VersionLifecycleState.DRAFT)throw new BizException(40960,"OPENAPI_PUBLISHED_PRODUCT_VERSION_IMMUTABLE");}
    private ApiProduct requireProduct(String id){ApiProduct p=productMapper.selectById(id);String t=SecurityContextHolder.getTenantId();if(p==null||(t!=null&&!t.equals(p.getTenantId())))throw new BizException(40460,"OPENAPI_PRODUCT_NOT_FOUND");return p;}
    private ApiProductVersion requireVersion(String id){ApiProductVersion v=versionMapper.selectById(id);if(v==null)throw new BizException(40461,"OPENAPI_PRODUCT_VERSION_NOT_FOUND");requireProduct(v.getApiProductId());return v;}
    private ArrayNode copyArray(JsonNode n){return n!=null&&n.isArray()?(ArrayNode)n.deepCopy():objectMapper.createArrayNode();} private JsonNode copyObject(JsonNode n){return n!=null&&n.isObject()?n.deepCopy():objectMapper.createObjectNode();}
    private String targetTenant(String r){String c=SecurityContextHolder.getTenantId();if(c==null)return StringUtils.hasText(r)?r.trim():null;if(StringUtils.hasText(r)&&!c.equals(r.trim()))throw new BizException(40310,"OPENAPI_CROSS_TENANT_ACCESS_DENIED");return c;}
    private void initialize(com.triobase.common.core.entity.BaseEntity e){LocalDateTime n=LocalDateTime.now();e.setCreatedBy(operator());e.setCreatedAt(n);e.setUpdatedBy(operator());e.setUpdatedAt(n);if(e instanceof com.triobase.service.openapi.domain.model.VersionedEntity v)v.setRowVersion(0L);}
    private void touch(com.triobase.common.core.entity.BaseEntity e){e.setUpdatedBy(operator());e.setUpdatedAt(LocalDateTime.now());}
    private String operator(){return StringUtils.hasText(SecurityContextHolder.getUserId())?SecurityContextHolder.getUserId():"SYSTEM";}
    private record ParsedVersion(String text,int major,int minor,int patch){}
}
