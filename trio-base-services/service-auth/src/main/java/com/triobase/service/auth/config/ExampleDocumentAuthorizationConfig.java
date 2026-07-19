package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.CustomDocumentAuthorizationManifest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reference example showing how to register hand-authored business documents
 * as authorization-protected resources.
 *
 * <p>Other services (billing, compliance, CRM) can follow the same pattern:
 * create a {@code @Configuration} class and declare one or more
 * {@code @Bean} methods that return {@link CustomDocumentAuthorizationManifest}.
 * The {@link AuthorizationStartupSyncRunner} picks them up automatically
 * and synchronizes them into the authorization registry at startup.</p>
 *
 * <p>The resource code prefix {@code CUSTOM_DOC:} distinguishes custom documents
 * from system-managed resources such as {@code LOWCODE_FORM:} or {@code LOWCODE_APP:}.</p>
 *
 * <h3>Registering a set of custom documents</h3>
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
 *         doc.setDisplayName("Invoice");
 *         doc.setBusinessObjectId("billing/invoice");
 *
 *         // Actions
 *         for (String code : List.of("VIEW", "CREATE", "EDIT", "DELETE", "EXPORT")) {
 *             var action = new CustomDocumentAuthorizationManifest.Action();
 *             action.setActionCode(code);
 *             doc.getActions().add(action);
 *         }
 *
 *         // Fields with sensitivity classifiers
 *         var amount = new CustomDocumentAuthorizationManifest.Field();
 *         amount.setFieldKey("amount");
 *         amount.setFieldLabel("Amount");
 *         amount.setFieldType("number");
 *         amount.setSensitivityClassification("FINANCIAL");
 *         amount.setDefaultMaskStrategy("LAST4");
 *         doc.getFields().add(amount);
 *
 *         manifest.getDocuments().add(doc);
 *         return manifest;
 *     }
 * }
 * }</pre>
 */
@Configuration
public class ExampleDocumentAuthorizationConfig {

    /**
     * Declares a sample "Contract" document resource with actions and fields.
     * This is a reference — in production, each owning service would declare
     * its own manifest beans.
     */
    @Bean
    CustomDocumentAuthorizationManifest referenceContractManifest() {
        CustomDocumentAuthorizationManifest manifest = new CustomDocumentAuthorizationManifest();
        manifest.setTenantId("GLOBAL");
        manifest.setServiceName("service-openapi");

        CustomDocumentAuthorizationManifest.Document doc = new CustomDocumentAuthorizationManifest.Document();
        doc.setCode("CUSTOM_DOC:CONTRACT");
        doc.setDocumentType("CUSTOM_DOC");
        doc.setDisplayName("Reference Contract");
        doc.setBusinessObjectId("openapi/reference-contract");

        // Actions
        for (String code : new String[]{"VIEW", "CREATE", "EDIT", "SUBMIT", "APPROVE", "EXPORT"}) {
            CustomDocumentAuthorizationManifest.Action action = new CustomDocumentAuthorizationManifest.Action();
            action.setActionCode(code);
            if ("APPROVE".equals(code) || "REJECT".equals(code)) {
                action.setGuardCodes(java.util.List.of("WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL"));
            }
            doc.getActions().add(action);
        }

        // Fields
        addField(doc, "amount", "Amount", "number", "FINANCIAL", "LAST4");
        addField(doc, "customerName", "Customer Name", "string", null, null);
        addField(doc, "paymentTerms", "Payment Terms", "string", "INTERNAL", null);

        // Guards
        for (String code : new String[]{"DOCUMENT_STATUS", "ARCHIVED_LOCK"}) {
            CustomDocumentAuthorizationManifest.Guard guard = new CustomDocumentAuthorizationManifest.Guard();
            guard.setGuardCode(code);
            guard.setOwnerService("service-openapi");
            guard.setSupportedResourceTypes("CUSTOM_DOC");
            guard.setDescription(code.equals("DOCUMENT_STATUS") ? "文档状态必须允许当前操作" : "已归档文档不可编辑");
            doc.getGuards().add(guard);
        }

        manifest.getDocuments().add(doc);
        return manifest;
    }

    private static void addField(CustomDocumentAuthorizationManifest.Document doc,
                                  String key,
                                  String label,
                                  String type,
                                  String sensitivity,
                                  String maskStrategy) {
        CustomDocumentAuthorizationManifest.Field field = new CustomDocumentAuthorizationManifest.Field();
        field.setFieldKey(key);
        field.setFieldLabel(label);
        field.setFieldType(type);
        field.setSensitivityClassification(sensitivity);
        field.setDefaultMaskStrategy(maskStrategy);
        doc.getFields().add(field);
    }
}
