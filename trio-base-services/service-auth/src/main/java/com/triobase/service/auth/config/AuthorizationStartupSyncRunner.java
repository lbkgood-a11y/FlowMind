package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.common.dto.authz.CustomDocumentAuthorizationManifest;
import com.triobase.service.auth.service.AuthorizationRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans all {@link CustomDocumentAuthorizationManifest} beans and synchronizes their
 * document resources into the authorization registry at startup.
 *
 * <p>Services that own hand-authored business documents (invoices, contracts, etc.)
 * declare them as {@code @Bean} definitions of {@link CustomDocumentAuthorizationManifest}.
 * This runner picks them up automatically at startup and persists the resources,
 * actions, fields, and guards via the {@link AuthorizationRegistryService}.</p>
 *
 * <p>To register custom document resources, add a {@code @Bean} method to any
 * {@code @Configuration} class visible to the auth service's component scan:</p>
 * <pre>{@code
 * @Configuration
 * public class InvoiceAuthorizationConfig {
 *     @Bean
 *     CustomDocumentAuthorizationManifest invoiceManifest() {
 *         var manifest = new CustomDocumentAuthorizationManifest();
 *         manifest.setTenantId("GLOBAL");
 *         manifest.setServiceName("service-billing");
 *
 *         var doc = new CustomDocumentAuthorizationManifest.Document();
 *         doc.setCode("CUSTOM_DOC:INVOICE");
 *         doc.setDocumentType("CUSTOM_DOC");
 *         doc.setDisplayName("Invoice");
 *         doc.setBusinessObjectId("billing/invoice");
 *
 *         var view = new CustomDocumentAuthorizationManifest.Action();
 *         view.setActionCode("VIEW");
 *         doc.getActions().add(view);
 *         var edit = new CustomDocumentAuthorizationManifest.Action();
 *         edit.setActionCode("EDIT");
 *         doc.getActions().add(edit);
 *
 *         var amountField = new CustomDocumentAuthorizationManifest.Field();
 *         amountField.setFieldKey("amount");
 *         amountField.setFieldLabel("Amount");
 *         amountField.setFieldType("number");
 *         amountField.setSensitivityClassification("FINANCIAL");
 *         amountField.setDefaultMaskStrategy("LAST4");
 *         doc.getFields().add(amountField);
 *
 *         manifest.getDocuments().add(doc);
 *         return manifest;
 *     }
 * }
 * }</pre>
 */
@Component
public class AuthorizationStartupSyncRunner {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationStartupSyncRunner.class);

    private final List<CustomDocumentAuthorizationManifest> manifests;
    private final AuthorizationRegistryService registryService;

    public AuthorizationStartupSyncRunner(
            List<CustomDocumentAuthorizationManifest> manifests,
            AuthorizationRegistryService registryService) {
        this.manifests = manifests;
        this.registryService = registryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (manifests.isEmpty()) {
            logger.debug("No CustomDocumentAuthorizationManifest beans found — skipping startup sync.");
            return;
        }
        int totalDocs = 0;
        for (CustomDocumentAuthorizationManifest manifest : manifests) {
            if (manifest == null || !StringUtils.hasText(manifest.getServiceName())) {
                logger.warn("Skipping null or service-less manifest");
                continue;
            }
            List<AuthorizationResourceSyncRequest.Resource> resources = toResources(manifest);
            if (resources.isEmpty()) {
                continue;
            }
            AuthorizationResourceSyncRequest request = new AuthorizationResourceSyncRequest();
            request.setTenantId(StringUtils.hasText(manifest.getTenantId())
                    ? manifest.getTenantId().trim() : "default");
            request.setOwnerService(manifest.getServiceName().trim());
            request.setResources(resources);
            try {
                registryService.synchronize(request);
                totalDocs += resources.size();
                logger.info("Synced {} resource(s) from service '{}'",
                        resources.size(), manifest.getServiceName());
            } catch (RuntimeException e) {
                logger.warn("Failed to sync resources for service '{}': {}",
                        manifest.getServiceName(), e.getMessage());
            }
        }
        if (totalDocs > 0) {
            logger.info("Authorization startup sync complete: {} resource(s) registered.", totalDocs);
        }
    }

    private List<AuthorizationResourceSyncRequest.Resource> toResources(
            CustomDocumentAuthorizationManifest manifest) {
        List<AuthorizationResourceSyncRequest.Resource> resources = new ArrayList<>();
        for (CustomDocumentAuthorizationManifest.Document doc : manifest.getDocuments()) {
            if (doc == null || !StringUtils.hasText(doc.getCode())) {
                continue;
            }
            AuthorizationResourceSyncRequest.Resource resource = new AuthorizationResourceSyncRequest.Resource();
            resource.setResourceCode(doc.getCode().trim());
            resource.setResourceType(StringUtils.hasText(doc.getDocumentType())
                    ? doc.getDocumentType().trim().toUpperCase() : "CUSTOM_DOC");
            resource.setDisplayName(StringUtils.hasText(doc.getDisplayName()) ? doc.getDisplayName().trim() : doc.getCode());
            resource.setBusinessObjectId(doc.getBusinessObjectId());
            resource.setLifecycleStatus(StringUtils.hasText(doc.getLifecycleStatus())
                    ? doc.getLifecycleStatus().trim().toUpperCase() : "ACTIVE");
            resource.setMetadataJson(doc.getMetadataJson());

            if (doc.getActions() != null) {
                for (CustomDocumentAuthorizationManifest.Action src : doc.getActions()) {
                    if (src == null || !StringUtils.hasText(src.getActionCode())) {
                        continue;
                    }
                    AuthorizationResourceSyncRequest.Action action = new AuthorizationResourceSyncRequest.Action();
                    action.setActionCode(src.getActionCode().trim().toUpperCase());
                    action.setActionCategory("DOCUMENT");
                    action.setDescription(src.getDescription());
                    if (src.getGuardCodes() != null) {
                        action.setGuardCodes(src.getGuardCodes().stream()
                                .filter(StringUtils::hasText)
                                .map(String::trim)
                                .toList());
                    }
                    resource.getActions().add(action);
                }
            }

            if (doc.getFields() != null) {
                for (CustomDocumentAuthorizationManifest.Field src : doc.getFields()) {
                    if (src == null || !StringUtils.hasText(src.getFieldKey())) {
                        continue;
                    }
                    AuthorizationResourceSyncRequest.Field field = new AuthorizationResourceSyncRequest.Field();
                    field.setFieldKey(src.getFieldKey().trim());
                    field.setFieldLabel(src.getFieldLabel());
                    field.setFieldType(src.getFieldType());
                    field.setSensitivityClassification(src.getSensitivityClassification());
                    field.setDefaultMaskStrategy(src.getDefaultMaskStrategy());
                    resource.getFields().add(field);
                }
            }

            if (doc.getGuards() != null) {
                for (CustomDocumentAuthorizationManifest.Guard src : doc.getGuards()) {
                    if (src == null || !StringUtils.hasText(src.getGuardCode())) {
                        continue;
                    }
                    AuthorizationResourceSyncRequest.Guard guard = new AuthorizationResourceSyncRequest.Guard();
                    guard.setGuardCode(src.getGuardCode().trim().toUpperCase());
                    guard.setOwnerService(StringUtils.hasText(src.getOwnerService())
                            ? src.getOwnerService().trim() : manifest.getServiceName());
                    guard.setSupportedResourceTypes(src.getSupportedResourceTypes());
                    guard.setConfigSchemaJson(src.getConfigSchemaJson());
                    guard.setDescription(src.getDescription());
                    resource.getGuards().add(guard);
                }
            }
            resources.add(resource);
        }
        return resources;
    }
}
