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
    fieldCount?: number;
    fieldEnforcementReadiness?: 'NON_COMPLIANT' | 'NOT_APPLICABLE' | 'PARTIAL' | 'READY';
    fieldEnforcementReason?: string;
    guards: GuardNode[];
    id: string;
    lastSyncedAt?: string;
    lifecycleStatus?: string;
    metadataJson?: string;
    ownerService?: string;
    resourceCode: string;
    resourceType: string;
    readHideEnforced?: boolean;
    readMaskEnforced?: boolean;
    writeDenyEnforced?: boolean;
  }

  export interface ResourceSummary {
    businessObjectId?: string;
    displayName?: string;
    globalResource?: boolean;
    fieldCount?: number;
    fieldEnforcementReadiness?: 'NON_COMPLIANT' | 'NOT_APPLICABLE' | 'PARTIAL' | 'READY';
    fieldEnforcementReason?: string;
    id: string;
    lastSyncedAt?: string;
    lifecycleStatus?: string;
    metadataJson?: string;
    ownerService?: string;
    readHideEnforced?: boolean;
    readMaskEnforced?: boolean;
    resourceCode: string;
    resourceType: string;
    tenantId?: string;
    writeDenyEnforced?: boolean;
  }

  export interface ResourcePage {
    page: number;
    records: ResourceSummary[];
    size: number;
    total: number;
  }

  export interface ResourceQuery {
    keyword?: string;
    lifecycleStatus?: string;
    ownerService?: string;
    page?: number;
    resourceType?: string;
    size?: number;
    tenantId?: string;
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
    grantVersion?: number;
    tenantId?: string;
  }

  export interface ReplaceRoleFunctionGrants {
    expectedGrantVersion?: number;
    grants: Array<{
      actionCode: string;
      description?: string;
      resourceCode: string;
    }>;
    tenantId?: string;
  }

  export interface ReplaceRoleFunctionGrantsResult {
    authorizationVersion: number;
    grantVersion: number;
    persistedCount: number;
    roleId: string;
  }

  export interface LowcodeAuthorizationBundleRequest {
    applicationResourceCode: string;
    idempotencyKey?: string;
    preset: 'ADMIN' | 'APPLICANT' | 'APPROVER' | 'DESIGNER';
    roleId: string;
    tenantId?: string;
  }

  export interface LowcodeAuthorizationBundleResult {
    applicable: boolean;
    applicationResourceCode: string;
    applied: boolean;
    authorizationVersion: number;
    changes: Array<{
      actionCode: string;
      resourceCode: string;
      state: 'ADD' | 'UNCHANGED';
    }>;
    formResourceCode: string;
    preset: string;
    replayed: boolean;
    roleId: string;
    tenantId: string;
  }

  export interface LowcodeAuthorizationPublication {
    acknowledgedAt?: string;
    acknowledgedRevision?: number;
    aggregateId: string;
    aggregateType: string;
    aggregateVersion: number;
    attemptCount: number;
    eventId: string;
    lastError?: string;
    operation: string;
    snapshotHash: string;
    status: string;
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
    evaluationMode?: 'ACTUAL_USER' | 'SIMULATION';
    simulatedRoleId?: string;
    suppliedOrganizationIds?: string[];
    menuDerivation?: MenuDerivation[];
  }

  export interface MenuDerivation {
    actionCode?: string;
    derivation: 'ANCESTOR' | 'DIRECT_GRANT';
    derivedFromMenuIds?: string[];
    menuId: string;
    menuName?: string;
    permissionCode?: string;
    resourceCode?: string;
  }

  export interface RoleSimulationPreviewRequest extends DecisionPreviewRequest {
    organizationIds?: string[];
    roleId: string;
  }

  export type PageCapabilityCategory = 'ACCESS' | 'OPERATION' | 'READ';

  export interface PageCapability {
    availableFields?: Array<{ fieldKey: string; fieldLabel?: string }>;
    category: PageCapabilityCategory;
    capabilityName: string;
    capabilityCode: string;
    constraintConfigurable?: boolean;
    fieldRestrictionConfigurable?: boolean;
    helpText?: string;
    id: string;
    pageCode: string;
    pageName: string;
    readiness: 'BROKEN' | 'PARTIAL' | 'READY' | 'UNMAPPED';
    readinessMessage?: string;
    requiredCapabilityIds?: string[];
    scopeConfigurable?: boolean;
    sortOrder?: number;
  }

  export interface PageCapabilityCatalog {
    activatedAt?: string;
    catalogCode: string;
    catalogVersion: number;
    createdAt?: string;
    id: string;
    lifecycleStatus: 'ACTIVE' | 'DRAFT' | 'SUPERSEDED';
    manifestHash: string;
    sourceRef?: string;
    sourceType: string;
    tenantId: string;
    updatedAt?: string;
  }

  export interface RoleCapabilitySelection {
    capabilityId: string;
    capabilityName?: string;
    category?: PageCapabilityCategory;
    defaultScopeIds?: string[];
    defaultScopeType?: string;
    effectiveScopeSummary?: string;
    fieldIntentJson?: string;
    operationScopeIds?: string[];
    operationScopeType?: string;
    selectionSource?: 'DEPENDENCY' | 'EXPLICIT' | 'MIGRATION';
  }

  export interface RoleAuthorizationDraft {
    basedReleaseId?: string;
    catalogId: string;
    draftId: string;
    roleId: string;
    selections: RoleCapabilitySelection[];
    status: 'DRAFT' | 'FAILED' | 'PUBLISHED' | 'PUBLISHING' | 'VALIDATED';
    validatedAt?: string;
    validationExpiresAt?: string;
    version: number;
  }

  export interface CompilationPlan {
    businessSummary: string;
    dataPolicies: unknown[];
    fieldPolicies: unknown[];
    grants: unknown[];
    guards: unknown[];
  }

  export interface RoleAuthorizationValidation {
    affectedUserCount: number;
    blockingErrors: string[];
    businessSummary: string;
    compilation: CompilationPlan;
    expiresAt: string;
    validationToken: string;
    warnings: string[];
  }

  export interface RoleAuthorizationRelease {
    businessSummary: string;
    catalogVersion: number;
    intentVersion: number;
    publishedAt: string;
    publishedBy: string;
    releaseId: string;
    releaseNumber: number;
    roleId: string;
  }

  export interface PageCapabilitySimulation {
    allowed: boolean;
    capabilityName: string;
    dataScopeSummary: string;
    evaluationMode: string;
    fieldSummaries: string[];
    guardSummaries: string[];
    outcome: string;
    pageName: string;
    reasons: string[];
  }

  export interface PageCapabilityDiagnostic {
    capabilityCode: string;
    capabilityId: string;
    catalogId: string;
    catalogVersion: number;
    pageCode: string;
    readiness: 'BROKEN' | 'PARTIAL' | 'READY' | 'UNMAPPED';
    readinessMessage?: string;
    requiredCapabilityCodes: string[];
    targets: Array<{
      actionCode: string;
      active: boolean;
      required: boolean;
      resourceCode: string;
      targetKind: string;
    }>;
    tenantId: string;
  }

  export interface RoleAuthorizationDrift {
    affectedUserCount: number;
    capabilityCode: string;
    detectedAt: string;
    driftId: string;
    driftType: string;
    impactSummary: string;
    roleId: string;
    status: string;
  }

  export interface AuthorizationManagementMode {
    managementMode: 'LEGACY' | 'MIGRATION' | 'PAGE_CAPABILITY';
    tenantId: string;
    updatedAt?: string;
    updatedBy?: string;
  }

  export interface AuthorizationCompatibilityDashboard {
    blockers: string[];
    catalogCapabilityCount: number;
    catalogNotReadyCount: number;
    catalogReadyCount: number;
    cutoverReady: boolean;
    decisionEquivalentRoleCount: number;
    decisionMismatchRoleCount: number;
    missingProjectionCount: number;
    openDriftCount: number;
    pendingMigrationRoleCount: number;
    publicationFailureCount: number;
    publishedRoleCount: number;
    roleStatuses: Array<{
      missingProjectionCount: number;
      roleId: string;
      roleName: string;
      status: 'EQUIVALENT' | 'MISMATCH' | 'PENDING_MIGRATION';
      unintendedExpansionCount: number;
    }>;
    rollbackCount: number;
    tenantId: string;
    totalRoleCount: number;
    unintendedExpansionCount: number;
    unresolvedExpansionReviewCount: number;
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

async function getAuthorizationResources(
  params?: SystemAuthorizationApi.ResourceQuery,
) {
  return requestClient.get<SystemAuthorizationApi.ResourcePage>(
    '/authz/resources',
    { params },
  );
}

async function getStaleAuthorizationResources(params?: {
  ownerService?: string;
  staleMinutes?: number;
  tenantId?: string;
}) {
  return requestClient.get<SystemAuthorizationApi.ResourceSummary[]>(
    '/authz/resources/stale',
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

async function replaceRoleFunctionGrants(
  roleId: string,
  data: SystemAuthorizationApi.ReplaceRoleFunctionGrants,
) {
  return requestClient.put<SystemAuthorizationApi.ReplaceRoleFunctionGrantsResult>(
    `/authz/roles/${roleId}/function-grants`,
    data,
  );
}

async function previewLowcodeAuthorizationBundle(
  data: SystemAuthorizationApi.LowcodeAuthorizationBundleRequest,
) {
  return requestClient.post<SystemAuthorizationApi.LowcodeAuthorizationBundleResult>(
    '/authz/lowcode-application-bundles/preview',
    data,
  );
}

async function applyLowcodeAuthorizationBundle(
  data: SystemAuthorizationApi.LowcodeAuthorizationBundleRequest,
) {
  return requestClient.post<SystemAuthorizationApi.LowcodeAuthorizationBundleResult>(
    '/authz/lowcode-application-bundles/apply',
    data,
  );
}

async function getLowcodeAuthorizationPublications() {
  return requestClient.get<SystemAuthorizationApi.LowcodeAuthorizationPublication[]>(
    '/lowcode-authorization-publications',
  );
}

async function retryLowcodeAuthorizationPublication(eventId: string) {
  return requestClient.post(`/lowcode-authorization-publications/${eventId}/retry`);
}

async function reconcileLowcodeAuthorizationPublication(eventId: string) {
  return requestClient.post(`/lowcode-authorization-publications/${eventId}/reconcile`);
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

async function previewRoleAuthorizationDecision(
  data: SystemAuthorizationApi.RoleSimulationPreviewRequest,
) {
  return requestClient.post<SystemAuthorizationApi.DecisionPreview>(
    '/authz/decisions/role-simulation',
    data,
  );
}

async function getPageCapabilities(
  tenantId?: string,
  pageCode?: string,
  catalogId?: string,
) {
  return requestClient.get<SystemAuthorizationApi.PageCapability[]>(
    '/authz/page-capabilities',
    { params: { catalogId, pageCode, tenantId } },
  );
}

async function getPageCapabilityDiagnostics(
  tenantId?: string,
  catalogId?: string,
) {
  return requestClient.get<SystemAuthorizationApi.PageCapabilityDiagnostic[]>(
    '/authz/page-capabilities/diagnostics',
    { params: { catalogId, tenantId } },
  );
}

async function getPageCapabilityCatalogs(tenantId?: string) {
  return requestClient.get<SystemAuthorizationApi.PageCapabilityCatalog[]>(
    '/authz/page-capabilities/catalogs',
    { params: { tenantId } },
  );
}

async function getAuthorizationCompatibilityDashboard(tenantId?: string) {
  return requestClient.get<SystemAuthorizationApi.AuthorizationCompatibilityDashboard>(
    '/authz/compatibility-dashboard',
    { params: { tenantId } },
  );
}

async function getAuthorizationManagementMode(tenantId?: string) {
  return requestClient.get<SystemAuthorizationApi.AuthorizationManagementMode>(
    '/authz/management-mode',
    { params: { tenantId } },
  );
}

async function updateAuthorizationManagementMode(
  mode: SystemAuthorizationApi.AuthorizationManagementMode['managementMode'],
  tenantId?: string,
) {
  return requestClient.put<SystemAuthorizationApi.AuthorizationManagementMode>(
    '/authz/management-mode',
    undefined,
    { params: { mode, tenantId } },
  );
}

async function getOrCreateRoleAuthorizationDraft(
  roleId: string,
  tenantId?: string,
) {
  return requestClient.post<SystemAuthorizationApi.RoleAuthorizationDraft>(
    `/authz/roles/${roleId}/authorization-drafts`,
    undefined,
    { params: { tenantId } },
  );
}

async function replaceRoleCapabilityIntent(
  draftId: string,
  data: {
    expectedVersion: number;
    removedCapabilityIds?: string[];
    selections: SystemAuthorizationApi.RoleCapabilitySelection[];
    tenantId?: string;
  },
) {
  return requestClient.put<SystemAuthorizationApi.RoleAuthorizationDraft>(
    `/authz/role-authorization-drafts/${draftId}/intent`,
    data,
  );
}

async function validateRoleAuthorizationDraft(
  draftId: string,
  data: { expectedVersion: number; tenantId?: string },
) {
  return requestClient.post<SystemAuthorizationApi.RoleAuthorizationValidation>(
    `/authz/role-authorization-drafts/${draftId}/validate`,
    data,
  );
}

async function publishRoleAuthorizationDraft(
  draftId: string,
  data: { expectedVersion: number; tenantId?: string; validationToken: string },
) {
  return requestClient.post<SystemAuthorizationApi.RoleAuthorizationRelease>(
    `/authz/role-authorization-drafts/${draftId}/publish`,
    data,
  );
}

async function getRoleAuthorizationReleases(roleId: string, tenantId?: string) {
  return requestClient.get<SystemAuthorizationApi.RoleAuthorizationRelease[]>(
    `/authz/roles/${roleId}/authorization-releases`,
    { params: { tenantId } },
  );
}

async function getRoleAuthorizationDrifts(roleId: string, tenantId?: string) {
  return requestClient.get<SystemAuthorizationApi.RoleAuthorizationDrift[]>(
    '/authz/page-capabilities/drifts',
    { params: { roleId, tenantId } },
  );
}

async function rollbackRoleAuthorizationRelease(
  roleId: string,
  releaseId: string,
  tenantId?: string,
) {
  return requestClient.post<SystemAuthorizationApi.RoleAuthorizationRelease>(
    `/authz/roles/${roleId}/authorization-releases/${releaseId}/rollback`,
    undefined,
    { params: { tenantId } },
  );
}

async function simulatePageCapability(
  capabilityId: string,
  data: {
    businessObjectId?: string;
    mode: 'ROLE' | 'USER';
    organizationIds?: string[];
    roleId?: string;
    tenantId?: string;
    userId?: string;
  },
) {
  return requestClient.post<SystemAuthorizationApi.PageCapabilitySimulation>(
    `/authz/page-capabilities/${capabilityId}/simulate`,
    data,
  );
}

export {
  applyLowcodeAuthorizationBundle,
  deleteAuthorizationFieldPolicy,
  deleteAuthorizationGrant,
  getAuthorizationAdminOptions,
  getAuthorizationCompatibilityDashboard,
  getAuthorizationManagementMode,
  getAuthorizationResources,
  getAuthorizationResourceTree,
  getStaleAuthorizationResources,
  getLowcodeAuthorizationPublications,
  getOrCreateRoleAuthorizationDraft,
  getPageCapabilities,
  getPageCapabilityCatalogs,
  getPageCapabilityDiagnostics,
  getRoleAuthorizationReleases,
  getRoleAuthorizationDrifts,
  getRoleAuthorizationProfile,
  previewAuthorizationDecision,
  previewLowcodeAuthorizationBundle,
  previewRoleAuthorizationDecision,
  publishRoleAuthorizationDraft,
  replaceRoleCapabilityIntent,
  replaceRoleFunctionGrants,
  reconcileLowcodeAuthorizationPublication,
  retryLowcodeAuthorizationPublication,
  saveAuthorizationFieldPolicy,
  saveAuthorizationGrant,
  saveAuthorizationGuardTemplate,
  rollbackRoleAuthorizationRelease,
  simulatePageCapability,
  updateAuthorizationGuardTemplateStatus,
  updateAuthorizationManagementMode,
  validateRoleAuthorizationDraft,
};
