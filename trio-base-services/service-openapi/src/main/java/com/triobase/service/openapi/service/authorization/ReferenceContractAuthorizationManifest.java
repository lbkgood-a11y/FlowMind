package com.triobase.service.openapi.service.authorization;

import com.triobase.common.dto.authz.CustomDocumentAuthorizationManifest;

import java.util.List;

public final class ReferenceContractAuthorizationManifest {

    public static final String SERVICE_NAME = "service-openapi";
    public static final String RESOURCE_CODE = "CUSTOM_DOC:CONTRACT";

    private ReferenceContractAuthorizationManifest() {
    }

    public static CustomDocumentAuthorizationManifest create(String tenantId) {
        CustomDocumentAuthorizationManifest manifest = new CustomDocumentAuthorizationManifest();
        manifest.setTenantId(tenantId);
        manifest.setServiceName(SERVICE_NAME);
        manifest.setDocuments(List.of(document()));
        return manifest;
    }

    private static CustomDocumentAuthorizationManifest.Document document() {
        CustomDocumentAuthorizationManifest.Document document = new CustomDocumentAuthorizationManifest.Document();
        document.setCode(RESOURCE_CODE);
        document.setDocumentType("CONTRACT");
        document.setDisplayName("合同单据");
        document.setBusinessObjectId("CONTRACT");
        document.setMetadataJson("{\"documentType\":\"CONTRACT\",\"reference\":true}");
        document.setActions(List.of(
                action("VIEW", "查看合同", List.of()),
                action("CREATE", "创建合同", List.of()),
                action("EDIT", "编辑合同", List.of("DOCUMENT_STATUS", "ARCHIVED_LOCK")),
                action("SUBMIT", "提交合同", List.of("DOCUMENT_STATUS")),
                action("APPROVE", "审批合同", List.of("WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL", "DOCUMENT_STATUS")),
                action("EXPORT", "导出合同", List.of())
        ));
        document.setFields(List.of(
                field("amount", "合同金额", "number", "FINANCIAL", "LAST4"),
                field("customerName", "客户名称", "string", "BUSINESS", null),
                field("paymentTerms", "付款条款", "string", "CONFIDENTIAL", "MASK")
        ));
        document.setGuards(List.of(
                guard("DOCUMENT_STATUS", SERVICE_NAME, "CUSTOM_DOC", "合同状态必须允许当前操作"),
                guard("ARCHIVED_LOCK", SERVICE_NAME, "CUSTOM_DOC", "已归档合同不可编辑"),
                guard("WORKFLOW_CANDIDATE", "service-workflow-engine", "CUSTOM_DOC,WORKFLOW_TASK",
                        "当前用户必须是待办任务候选人或处理人"),
                guard("NO_SELF_APPROVAL", "service-workflow-engine", "CUSTOM_DOC,WORKFLOW_TASK",
                        "发起人不可审批自己的合同")
        ));
        return document;
    }

    private static CustomDocumentAuthorizationManifest.Action action(
            String actionCode,
            String description,
            List<String> guardCodes) {
        CustomDocumentAuthorizationManifest.Action action = new CustomDocumentAuthorizationManifest.Action();
        action.setActionCode(actionCode);
        action.setDescription(description);
        action.setGuardCodes(guardCodes);
        return action;
    }

    private static CustomDocumentAuthorizationManifest.Field field(
            String fieldKey,
            String label,
            String type,
            String sensitivity,
            String maskStrategy) {
        CustomDocumentAuthorizationManifest.Field field = new CustomDocumentAuthorizationManifest.Field();
        field.setFieldKey(fieldKey);
        field.setFieldLabel(label);
        field.setFieldType(type);
        field.setSensitivityClassification(sensitivity);
        field.setDefaultMaskStrategy(maskStrategy);
        return field;
    }

    private static CustomDocumentAuthorizationManifest.Guard guard(
            String guardCode,
            String ownerService,
            String supportedResourceTypes,
            String description) {
        CustomDocumentAuthorizationManifest.Guard guard = new CustomDocumentAuthorizationManifest.Guard();
        guard.setGuardCode(guardCode);
        guard.setOwnerService(ownerService);
        guard.setSupportedResourceTypes(supportedResourceTypes);
        guard.setDescription(description);
        return guard;
    }
}
