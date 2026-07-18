package com.triobase.service.openapi.controller;
import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.domain.entity.ApiProduct;
import com.triobase.service.openapi.domain.entity.ApiProductAccessGrant;
import com.triobase.service.openapi.domain.entity.AssetApproval;
import com.triobase.service.openapi.domain.entity.PolicyEnforcementState;
import com.triobase.service.openapi.domain.entity.PolicySnapshot;
import com.triobase.service.openapi.domain.entity.TrafficPolicyVersion;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.*;
import com.triobase.service.openapi.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/openapi/management") @RequiredArgsConstructor
public class ApplicationAccessManagementController {
 private static final String PRODUCT_READ="/api/v1/openapi/management/products:GET",PRODUCT_WRITE="/api/v1/openapi/management/products:POST",APP_READ="/api/v1/openapi/management/applications:GET",APP_WRITE="/api/v1/openapi/management/applications:POST",APPROVE="/api/v1/openapi/management/approvals:POST";
 private final ApiProductService productService;private final ApplicationManagementService applicationService;private final ApplicationCredentialService credentialService;private final ProductSubscriptionService subscriptionService;private final AssetApprovalService approvalService;private final TrafficPolicyService trafficPolicyService;private final PolicySnapshotService snapshotService;
 @PostMapping("/products")@RequirePermission(PRODUCT_WRITE) public R<ApiProductVersionResponse> createProduct(@Valid@RequestBody CreateApiProductRequest r){return R.ok(productService.create(r));}
 @PutMapping("/products/{id}")@RequirePermission(PRODUCT_WRITE) public R<ApiProduct> updateProduct(@PathVariable String id,@Valid@RequestBody UpdateApiProductRequest r){return R.ok(productService.updateProduct(id,r));}
 @PostMapping("/products/{id}/versions")@RequirePermission(PRODUCT_WRITE) public R<ApiProductVersionResponse> createProductDraft(@PathVariable String id,@Valid@RequestBody ApiProductVersionMutationRequest r){return R.ok(productService.createDraft(id,r));}
 @PutMapping("/products/versions/{id}")@RequirePermission(PRODUCT_WRITE) public R<ApiProductVersionResponse> updateProductDraft(@PathVariable String id,@Valid@RequestBody ApiProductVersionMutationRequest r){return R.ok(productService.updateDraft(id,r));}
 @GetMapping("/products/versions/{id}")@RequirePermission(PRODUCT_READ) public R<ApiProductVersionResponse> getProductVersion(@PathVariable String id){return R.ok(productService.getVersion(id));}
 @PostMapping("/products/versions/{id}/publish")@RequirePermission(PRODUCT_WRITE) public R<ApiProductVersionResponse> publishProduct(@PathVariable String id){return R.ok(productService.publish(id));}
 @PostMapping("/products/versions/{id}/deprecate")@RequirePermission(PRODUCT_WRITE) public R<ApiProductVersionResponse> deprecateProductVersion(@PathVariable String id){return R.ok(productService.deprecate(id));}
 @PostMapping("/products/versions/{id}/archive")@RequirePermission(PRODUCT_WRITE) public R<ApiProductVersionResponse> archiveProductVersion(@PathVariable String id){return R.ok(productService.archiveVersion(id));}
 @PostMapping("/products/{id}/archive")@RequirePermission(PRODUCT_WRITE) public R<Void> archiveProduct(@PathVariable String id){productService.archiveProduct(id);return R.ok();}
 @PostMapping("/products/{id}/grants/applications/{applicationId}")@RequirePermission(PRODUCT_WRITE) public R<ApiProductAccessGrant> grantProduct(@PathVariable String id,@PathVariable String applicationId){return R.ok(productService.grantPrivateAccess(id,applicationId));}
 @GetMapping("/products/discover")@RequirePermission(PRODUCT_READ) public R<List<ApiProduct>> discoverProducts(@RequestParam String applicationId){return R.ok(productService.discover(applicationId));}
 @PostMapping("/applications")@RequirePermission(APP_WRITE) public R<ApplicationResponse> createApplication(@Valid@RequestBody CreateApplicationRequest r){return R.ok(applicationService.create(r));}
 @GetMapping("/applications/{id}")@RequirePermission(APP_READ) public R<ApplicationResponse> getApplication(@PathVariable String id){return R.ok(applicationService.get(id));}
 @PutMapping("/applications/{id}")@RequirePermission(APP_WRITE) public R<ApplicationResponse> updateApplication(@PathVariable String id,@Valid@RequestBody UpdateApplicationRequest r){return R.ok(applicationService.update(id,r));}
 @PostMapping("/applications/{id}/submit")@RequirePermission(APP_WRITE) public R<ApplicationResponse> submitApplication(@PathVariable String id){return R.ok(applicationService.submit(id));}
 @PostMapping("/applications/{id}/activate")@RequirePermission(APP_WRITE) public R<ApplicationResponse> activateApplication(@PathVariable String id){return R.ok(applicationService.activate(id));}
 @PostMapping("/applications/{id}/suspend")@RequirePermission(APP_WRITE) public R<ApplicationResponse> suspendApplication(@PathVariable String id,@RequestBody(required=false)ActionReasonRequest r){return R.ok(applicationService.suspendApplication(id,r==null?null:r.reason()));}
 @PostMapping("/applications/{id}/reactivate")@RequirePermission(APP_WRITE) public R<ApplicationResponse> reactivateApplication(@PathVariable String id){return R.ok(applicationService.reactivateApplication(id));}
 @PostMapping("/applications/{id}/expire")@RequirePermission(APP_WRITE) public R<ApplicationResponse> expireApplication(@PathVariable String id){return R.ok(applicationService.expireApplication(id));}
 @PostMapping("/applications/{id}/revoke")@RequirePermission(APP_WRITE) public R<ApplicationResponse> revokeApplication(@PathVariable String id){return R.ok(applicationService.revokeApplication(id));}
 @PostMapping("/applications/{id}/clients")@RequirePermission(APP_WRITE) public R<ApplicationClientResponse> createClient(@PathVariable String id,@Valid@RequestBody CreateApplicationClientRequest r){return R.ok(applicationService.createClient(id,r));}
 @PostMapping("/applications/clients/{id}/activate")@RequirePermission(APP_WRITE) public R<ApplicationClientResponse> activateClient(@PathVariable String id){return R.ok(applicationService.activateClient(id));}
 @PostMapping("/applications/clients/{id}/suspend")@RequirePermission(APP_WRITE) public R<ApplicationClientResponse> suspendClient(@PathVariable String id,@RequestBody(required=false)ActionReasonRequest r){return R.ok(applicationService.suspendClient(id,r==null?null:r.reason()));}
 @PostMapping("/applications/clients/{id}/reactivate")@RequirePermission(APP_WRITE) public R<ApplicationClientResponse> reactivateClient(@PathVariable String id){return R.ok(applicationService.reactivateClient(id));}
 @PostMapping("/applications/clients/{id}/revoke")@RequirePermission(APP_WRITE) public R<ApplicationClientResponse> revokeClient(@PathVariable String id){return R.ok(applicationService.revokeClient(id));}
 @PostMapping("/applications/clients/{id}/credentials")@RequirePermission(APP_WRITE) public R<CredentialBindingResponse> bindCredential(@PathVariable String id,@Valid@RequestBody CreateCredentialBindingRequest r){return R.ok(credentialService.bindImported(id,r));}
 @PostMapping("/applications/clients/{id}/credentials/rotate")@RequirePermission(APP_WRITE) public R<CredentialBindingResponse> rotateCredential(@PathVariable String id,@Valid@RequestBody RotateCredentialRequest r){return R.ok(credentialService.rotateGenerated(id,r));}
 @GetMapping("/applications/clients/{id}/credentials")@RequirePermission(APP_READ) public R<List<CredentialBindingResponse>> credentials(@PathVariable String id){return R.ok(credentialService.list(id));}
 @PostMapping("/applications/credentials/{id}/revoke")@RequirePermission(APP_WRITE) public R<CredentialBindingResponse> revokeCredential(@PathVariable String id){return R.ok(credentialService.revoke(id));}
 @GetMapping("/applications/credentials/{id}/plaintext")@RequirePermission(APP_READ) public R<CredentialBindingResponse> plaintext(@PathVariable String id){return R.ok(credentialService.plaintext(id));}
 @PostMapping("/subscriptions")@RequirePermission(APP_WRITE) public R<SubscriptionResponse> requestSubscription(@Valid@RequestBody CreateSubscriptionRequest r){return R.ok(subscriptionService.request(r));}
 @PostMapping("/subscriptions/{id}/activate")@RequirePermission(APPROVE) public R<SubscriptionResponse> activateSubscription(@PathVariable String id){return R.ok(subscriptionService.activate(id));}
 @PostMapping("/subscriptions/{id}/upgrade")@RequirePermission(APP_WRITE) public R<SubscriptionResponse> upgradeSubscription(@PathVariable String id,@Valid@RequestBody UpgradeSubscriptionRequest r){return R.ok(subscriptionService.requestUpgrade(id,r.apiProductVersionId()));}
 @PostMapping("/subscriptions/{id}/suspend")@RequirePermission(APP_WRITE) public R<SubscriptionResponse> suspendSubscription(@PathVariable String id,@RequestBody(required=false)ActionReasonRequest r){return R.ok(subscriptionService.suspend(id,r==null?null:r.reason()));}
 @PostMapping("/subscriptions/{id}/revoke")@RequirePermission(APP_WRITE) public R<SubscriptionResponse> revokeSubscription(@PathVariable String id){return R.ok(subscriptionService.revoke(id));}
 @PostMapping("/subscriptions/{id}/expire")@RequirePermission(APP_WRITE) public R<SubscriptionResponse> expireSubscription(@PathVariable String id){return R.ok(subscriptionService.expire(id));}
 @PostMapping("/approvals/{id}/decision")@RequirePermission(APPROVE) public R<AssetApproval> decide(@PathVariable String id,@RequestBody ApprovalDecisionRequest r){return R.ok(approvalService.decide(id,r.approved(),r.evidence()));}
 @PostMapping("/policies")@RequirePermission(PRODUCT_WRITE) public R<TrafficPolicyVersion> createPolicy(@Valid@RequestBody CreateTrafficPolicyRequest r){return R.ok(trafficPolicyService.createDraft(r));}
 @PutMapping("/policies/{id}")@RequirePermission(PRODUCT_WRITE) public R<TrafficPolicyVersion> updatePolicy(@PathVariable String id,@Valid@RequestBody CreateTrafficPolicyRequest r){return R.ok(trafficPolicyService.updateDraft(id,r));}
 @PostMapping("/policies/{id}/publish")@RequirePermission(APPROVE) public R<TrafficPolicyVersion> publishPolicy(@PathVariable String id){return R.ok(trafficPolicyService.publish(id));}
 @PostMapping("/policies/snapshots")@RequirePermission(APPROVE) public R<PolicySnapshot> publishSnapshot(@RequestParam(required=false)String tenantId,@RequestParam Environment environment){return R.ok(snapshotService.publish(tenantId,environment));}
 @PostMapping("/policies/enforcement/{point}/applied")@RequirePermission(APPROVE) public R<PolicyEnforcementState> reportApplied(@PathVariable String point,@RequestParam(required=false)String tenantId,@RequestParam Environment environment,@RequestParam long version){return R.ok(snapshotService.reportApplied(point,tenantId,environment,version));}
 @GetMapping("/policies/enforcement/{point}")@RequirePermission(PRODUCT_READ) public R<PolicyEnforcementState> policyStatus(@PathVariable String point,@RequestParam(required=false)String tenantId,@RequestParam Environment environment){return R.ok(snapshotService.status(point,tenantId,environment));}
}
