import { requestClient } from '#/api/request';

export namespace SystemAuthorizationApi {
  export interface Option {
    category?: string;
    code: string;
    description?: string;
    label: string;
  }

  export interface GuardTemplate {
    configSchemaJson?: string;
    description?: string;
    guardCode: string;
    id?: string;
    ownerService?: string;
    status?: number;
    supportedResourceTypes?: string;
    tenantId?: string;
  }

  export interface AdminOptions {
    dataScopes: Option[];
    fieldReadModes: Option[];
    fieldWriteModes: Option[];
    functionActions: Option[];
    guardTemplates: GuardTemplate[];
    maskStrategies: Option[];
  }

  export interface ActionNode {
    actionCategory?: string;
    actionCode: string;
    description?: string;
    guardCodes?: string[];
    status?: number;
  }

  export interface FieldNode {
    defaultMaskStrategy?: string;
    fieldKey: string;
    fieldLabel?: string;
    fieldType?: string;
    sensitivityClassification?: string;
    status?: number;
  }

  export interface GuardNode {
    description?: string;
    guardCode: string;
    ownerService?: string;
    status?: number;
  }

  export interface ResourceNode {
    actions: ActionNode[];
    businessObjectId?: string;
    displayName?: string;
    fields: FieldNode[];
    guards: GuardNode[];
    id: string;
    lastSyncedAt?: string;
    lifecycleStatus?: string;
    ownerService?: string;
    resourceCode: string;
    resourceType: string;
  }

  export interface ResourceGroup {
    label: string;
    resources: ResourceNode[];
    resourceType: string;
  }

  export interface ResourceTree {
    groups: ResourceGroup[];
    tenantId?: string;
  }

  export interface AuthorizationGrant {
    actionCode: string;
    description?: string;
    effect: 'ALLOW' | 'DENY' | string;
    id?: string;
    resourceCode: string;
    status?: number;
    subjectId: string;
    subjectType: 'ROLE' | 'USER' | string;
    tenantId?: string;
  }

  export interface SaveAuthorizationGrant {
    actionCode: string;
    description?: string;
    effect: 'ALLOW' | 'DENY' | string;
    resourceCode: string;
    status?: number;
    subjectId: string;
    subjectType: 'ROLE' | 'USER' | string;
    tenantId?: string;
  }

  export interface DataPolicyDimension {
    dimensionCode: string;
    id?: string;
    orgUnitIds?: string[];
    scopeType: string;
    sortOrder?: number;
  }

  export interface DataPolicy {
    actionCode: string;
    combineMode: 'AND' | 'OR' | string;
    createdAt?: string;
    description?: string;
    dimensions?: DataPolicyDimension[];
    effect: 'ALLOW' | 'DENY' | string;
    id?: string;
    resourceCode: string;
    roleId: string;
    status?: number;
  }

  export interface FieldPolicy {
    description?: string;
    effect: 'ALLOW' | 'DENY' | string;
    fieldKey: string;
    id?: string;
    maskStrategy?: string;
    readMode: string;
    resourceCode: string;
    status?: number;
    subjectId: string;
    subjectType: 'ROLE' | 'USER' | string;
    tenantId?: string;
    writeMode: string;
  }

  export interface SaveFieldPolicy {
    description?: string;
    effect: 'ALLOW' | 'DENY' | string;
    fieldKey: string;
    maskStrategy?: string;
    readMode: string;
    resourceCode: string;
    status?: number;
    subjectId: string;
    subjectType: 'ROLE' | 'USER' | string;
    tenantId?: string;
    writeMode: string;
  }

  export interface RoleAuthorizationProfile {
    dataPolicies: DataPolicy[];
    fieldPolicies: FieldPolicy[];
    functionGrants: AuthorizationGrant[];
    roleId: string;
    tenantId?: string;
  }

  export interface SaveGuardTemplate {
    configSchemaJson?: string;
    description?: string;
    guardCode: string;
    ownerService?: string;
    status?: number;
    supportedResourceTypes?: string;
    tenantId?: string;
  }

  export interface DecisionReason {
    code: string;
    evidenceId?: string;
    message?: string;
    source?: string;
  }

  export interface DataScopeResult {
    orgContextResolved?: boolean;
    orgUnitIds?: string[];
    policyIds?: string[];
    restrictive?: boolean;
    roleIds?: string[];
    scopeTypes?: string[];
  }

  export interface FieldRule {
    fieldKey: string;
    maskStrategy?: string;
    matchedPolicyId?: string;
    readMode?: string;
    reasonCode?: string;
    reasonMessage?: string;
    writeMode?: string;
  }

  export interface GuardRequirement {
    configSchemaJson?: string;
    description?: string;
    guardCode: string;
    ownerService?: string;
  }

  export interface DecisionPreviewRequest {
    actionCode: string;
    attributes?: Record<string, unknown>;
    businessObjectId?: string;
    fieldKeys?: string[];
    ownerService?: string;
    resourceCode: string;
    tenantId?: string;
    userId?: string;
  }

  export interface DecisionPreview {
    actionCode: string;
    allowed: boolean;
    authorizationVersion?: number;
    businessObjectId?: string;
    dataPolicyVersion?: number;
    dataScope?: DataScopeResult;
    decisionId?: string;
    effect?: string;
    fieldPolicyVersion?: number;
    fieldRules?: FieldRule[];
    guardRequirements?: GuardRequirement[];
    guardTemplateVersion?: number;
    matchedGrantId?: string;
    ownerService?: string;
    reasons?: DecisionReason[];
    resourceCode: string;
    roleVersion?: number;
    tenantId?: string;
    userId?: string;
  }
}

async function getAuthorizationResourceTree(params?: {
  ownerService?: string;
  tenantId?: string;
}) {
  return requestClient.get<SystemAuthorizationApi.ResourceTree>(
    '/authz/resources/tree',
    { params },
  );
}

async function getAuthorizationAdminOptions(params?: {
  ownerService?: string;
  tenantId?: string;
}) {
  return requestClient.get<SystemAuthorizationApi.AdminOptions>(
    '/authz/configuration-options',
    { params },
  );
}

async function getRoleAuthorizationProfile(roleId: string, tenantId?: string) {
  return requestClient.get<SystemAuthorizationApi.RoleAuthorizationProfile>(
    `/authz/roles/${roleId}/authorization-profile`,
    { params: { tenantId } },
  );
}

async function saveAuthorizationGrant(
  data: SystemAuthorizationApi.SaveAuthorizationGrant,
) {
  return requestClient.post<SystemAuthorizationApi.AuthorizationGrant>(
    '/authz/grants',
    data,
  );
}

async function deleteAuthorizationGrant(id: string) {
  return requestClient.delete(`/authz/grants/${id}`);
}

async function saveAuthorizationFieldPolicy(
  data: SystemAuthorizationApi.SaveFieldPolicy,
) {
  return requestClient.post<SystemAuthorizationApi.FieldPolicy>(
    '/authz/field-policies',
    data,
  );
}

async function deleteAuthorizationFieldPolicy(id: string) {
  return requestClient.delete(`/authz/field-policies/${id}`);
}

async function saveAuthorizationGuardTemplate(
  data: SystemAuthorizationApi.SaveGuardTemplate,
) {
  return requestClient.post<SystemAuthorizationApi.GuardTemplate>(
    '/authz/guard-templates',
    data,
  );
}

async function updateAuthorizationGuardTemplateStatus(
  id: string,
  status: number,
) {
  return requestClient.put(`/authz/guard-templates/${id}/status`, undefined, {
    params: { status },
  });
}

async function previewAuthorizationDecision(
  data: SystemAuthorizationApi.DecisionPreviewRequest,
) {
  return requestClient.post<SystemAuthorizationApi.DecisionPreview>(
    '/authz/decisions/preview',
    data,
  );
}

export {
  deleteAuthorizationFieldPolicy,
  deleteAuthorizationGrant,
  getAuthorizationAdminOptions,
  getAuthorizationResourceTree,
  getRoleAuthorizationProfile,
  previewAuthorizationDecision,
  saveAuthorizationFieldPolicy,
  saveAuthorizationGrant,
  saveAuthorizationGuardTemplate,
  updateAuthorizationGuardTemplateStatus,
};
