<script setup lang="ts">
import type {
  SystemAuthorizationApi,
  SystemDataPolicyApi,
  SystemMenuApi,
  SystemOrgApi,
  SystemRoleApi,
} from '#/api';
import type { TableProps } from 'ant-design-vue';
import type { Dayjs } from 'dayjs';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  Button,
  Checkbox,
  DatePicker,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Pagination,
  Popconfirm,
  Popover,
  Radio,
  RadioGroup,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Tree,
} from 'ant-design-vue';

import {
  createDataPolicy,
  createRole,
  deleteAuthorizationFieldPolicy,
  deleteAuthorizationGrant,
  deleteDataPolicy,
  deleteRole,
  getAuthorizationAdminOptions,
  getAuthorizationResourceTree,
  getMenuList,
  getOrgDimensions,
  getOrgTree,
  getRoleAuthorizationProfile,
  getRoleDetail,
  getRolePage,
  previewAuthorizationDecision,
  roleCodeExists,
  saveAuthorizationFieldPolicy,
  saveAuthorizationGrant,
  updateAuthorizationGuardTemplateStatus,
  updateRole,
  updateRoleStatus,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import { BusinessPageScaffold } from '#/shared';

const RangePicker = DatePicker.RangePicker;
const TabPane = Tabs.TabPane;
const Textarea = Input.TextArea;

const ROLE_PERMISSIONS = {
  create: '/api/v1/roles:POST',
  delete: '/api/v1/roles/*:DELETE',
  query: '/api/v1/roles:GET',
  update: '/api/v1/roles/*:PUT',
} as const;

const MENU_PERMISSIONS = {
  query: '/api/v1/menus:GET',
} as const;

const AUTHZ_PERMISSIONS = {
  delete: '/api/v1/authz/**:DELETE',
  post: '/api/v1/authz/**:POST',
  put: '/api/v1/authz/**:PUT',
  query: '/api/v1/authz/**:GET',
} as const;

const DATA_POLICY_PERMISSIONS = {
  delete: '/api/v1/data-policies/*:DELETE',
  post: '/api/v1/data-policies:POST',
} as const;

const ORG_PERMISSIONS = {
  query: '/api/v1/org/units:GET',
} as const;

type RoleFormModel = {
  description?: string;
  roleCode: string;
  roleName: string;
  status: 0 | 1;
  visibleMenuIds: string[];
};

type CheckedKeysValue =
  | Array<number | string>
  | {
      checked: Array<number | string>;
      halfChecked: Array<number | string>;
    };

type RoleQueryModel = {
  createdRange?: [Dayjs, Dayjs];
  keyword?: string;
  roleCode?: string;
  roleName?: string;
  status?: 0 | 1;
};

type RoleColumnKey =
  | 'action'
  | 'createdAt'
  | 'description'
  | 'id'
  | 'roleCode'
  | 'roleName'
  | 'status';

type RoleColumnSetting = {
  key: RoleColumnKey;
  title: string;
  visible: boolean;
  width: number;
};

type RoleViewKey =
  | 'all'
  | 'disabled'
  | 'enabled'
  | 'system'
  | 'tenant'
  | 'user';

type RoleViewPreset = {
  description: string;
  label: string;
  query: {
    roleCode?: string;
    status?: 0 | 1;
  };
};

type RoleViewTreeNode = {
  children?: RoleViewTreeNode[];
  key: RoleViewKey | string;
  selectable?: boolean;
  title: string;
};

type AuthorizationTabKey =
  | 'data'
  | 'field'
  | 'function'
  | 'guard'
  | 'menu'
  | 'preview';

type AuthorizationTreeNode = {
  children?: AuthorizationTreeNode[];
  disableCheckbox?: boolean;
  disabled?: boolean;
  key: string;
  selectable?: boolean;
  title: string;
};

type MenuTreeNode = {
  children?: MenuTreeNode[];
  disabled?: boolean;
  key: string;
  selectable?: boolean;
  sortOrder?: number;
  title: string;
};

type ResourceActionItem = {
  action: SystemAuthorizationApi.ActionNode;
  key: string;
  resource: SystemAuthorizationApi.ResourceNode;
};

const defaultColumnSettings: RoleColumnSetting[] = [
  { key: 'roleName', title: '角色名称', visible: true, width: 180 },
  { key: 'roleCode', title: '角色编码', visible: true, width: 160 },
  { key: 'id', title: '角色ID', visible: true, width: 150 },
  { key: 'status', title: '状态', visible: true, width: 110 },
  { key: 'description', title: '描述', visible: true, width: 260 },
  { key: 'createdAt', title: '创建时间', visible: true, width: 210 },
  { key: 'action', title: '操作', visible: true, width: 230 },
];

const roleViewPresets: Record<RoleViewKey, RoleViewPreset> = {
  all: {
    description: '展示全部角色，右侧查询条件作为精确筛选。',
    label: '全部角色',
    query: {},
  },
  disabled: {
    description: '仅查看禁用角色，便于批量核查历史权限。',
    label: '已停用角色',
    query: { status: 0 },
  },
  enabled: {
    description: '仅查看当前启用角色，适合日常授权维护。',
    label: '启用角色',
    query: { status: 1 },
  },
  system: {
    description: '按 ADMIN 编码域筛选系统管理类角色。',
    label: '系统管理角色',
    query: { roleCode: 'ADMIN' },
  },
  tenant: {
    description: '按 TENANT 编码域筛选租户管理类角色。',
    label: '租户角色',
    query: { roleCode: 'TENANT' },
  },
  user: {
    description: '按 USER 编码域筛选普通访问类角色。',
    label: '普通用户角色',
    query: { roleCode: 'USER' },
  },
};

const roleViewTree: RoleViewTreeNode[] = [
  { key: 'all', title: roleViewPresets.all.label },
  {
    children: [
      { key: 'enabled', title: roleViewPresets.enabled.label },
      { key: 'disabled', title: roleViewPresets.disabled.label },
    ],
    key: 'status-group',
    selectable: false,
    title: '状态分组',
  },
  {
    children: [
      { key: 'system', title: roleViewPresets.system.label },
      { key: 'tenant', title: roleViewPresets.tenant.label },
      { key: 'user', title: roleViewPresets.user.label },
    ],
    key: 'domain-group',
    selectable: false,
    title: '角色域',
  },
];

const roles = ref<SystemRoleApi.SystemRole[]>([]);
const menus = ref<SystemMenuApi.SystemMenu[]>([]);
const loading = ref(false);
const loadingMenus = ref(false);
const authorizationLoading = ref(false);
const previewLoading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const detailOpen = ref(false);
const permissionOnly = ref(false);
const collapsed = ref(false);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const columnSettingOpen = ref(false);
const tableKey = ref(0);
const editingRole = ref<SystemRoleApi.RoleDetail>();
const detailRole = ref<SystemRoleApi.RoleDetail>();
const selectedRoleViewKey = ref<RoleViewKey>('all');
const activeAuthorizationTab = ref<AuthorizationTabKey>('menu');
const authorizationTree = ref<SystemAuthorizationApi.ResourceTree>();
const authorizationOptions = ref<SystemAuthorizationApi.AdminOptions>();
const authorizationProfile = ref<SystemAuthorizationApi.RoleAuthorizationProfile>();
const orgDimensions = ref<SystemOrgApi.OrgDimension[]>([]);
const orgOptionsMap = ref<Record<string, { label: string; value: string }[]>>({});
const functionGrantCheckedKeys = ref<CheckedKeysValue>([]);
const originalFunctionGrantIds = ref<Record<string, string>>({});
const previewResult = ref<SystemAuthorizationApi.DecisionPreview>();
const { hasAccessByCodes } = useAccess();

const canQuery = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.delete]));
const canQueryMenus = computed(() => hasAccessByCodes([MENU_PERMISSIONS.query]));
const canQueryAuthz = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.query]));
const canCreateAuthz = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.post]));
const canDeleteAuthz = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.delete]));
const canUpdateAuthz = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.put]));
const canCreateDataPolicy = computed(() =>
  hasAccessByCodes([DATA_POLICY_PERMISSIONS.post]),
);
const canDeleteDataPolicy = computed(() =>
  hasAccessByCodes([DATA_POLICY_PERMISSIONS.delete]),
);
const canQueryOrg = computed(() => hasAccessByCodes([ORG_PERMISSIONS.query]));
const canManageAuthz = computed(
  () => canCreateAuthz.value || canDeleteAuthz.value || canUpdateAuthz.value,
);
const canSaveRole = computed(() =>
  editingRole.value ? canUpdate.value : canCreate.value,
);
const canOpenAuthorization = computed(
  () => canUpdate.value && (canQueryMenus.value || canQueryAuthz.value),
);

const queryForm = reactive<RoleQueryModel>({
  createdRange: undefined,
  keyword: '',
  roleCode: '',
  roleName: '',
  status: undefined,
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive<RoleFormModel>({
  description: '',
  roleCode: '',
  roleName: '',
  status: 1,
  visibleMenuIds: [],
});

const columnSettings = reactive<RoleColumnSetting[]>(
  defaultColumnSettings.map((item) => ({ ...item })),
);
const columnDraft = ref<RoleColumnSetting[]>(
  defaultColumnSettings.map((item) => ({ ...item })),
);

const dataPolicyForm = reactive({
  actionCode: '',
  dimensionCode: 'ADMIN',
  orgUnitIds: [] as string[],
  resourceCode: '',
  scopeType: '',
});
const fieldPolicyForm = reactive({
  fieldKey: '',
  maskStrategy: '',
  readMode: '',
  resourceCode: '',
  writeMode: '',
});
const previewForm = reactive({
  actionCode: '',
  businessObjectId: '',
  resourceCode: '',
  userId: '',
});

const menuTree = computed(() => buildMenuTree(menus.value, true));
const visibleMenuCount = computed(() => formModel.visibleMenuIds.length);
const selectedRoleView = computed(
  () => roleViewPresets[selectedRoleViewKey.value],
);
const selectedRoleViewKeys = computed(() => [selectedRoleViewKey.value]);
const resourceGroups = computed(() => authorizationTree.value?.groups ?? []);
const resourceList = computed(() =>
  resourceGroups.value.flatMap((group) => group.resources ?? []),
);
const resourceOptions = computed(() =>
  resourceList.value.map((resource) => ({
    label: `${resource.displayName || resource.resourceCode} · ${resource.resourceType}`,
    value: resource.resourceCode,
  })),
);
const fieldResourceOptions = computed(() =>
  resourceList.value
    .filter((resource) => (resource.fields ?? []).length > 0)
    .map((resource) => ({
      label: `${resource.displayName || resource.resourceCode} · ${resource.resourceType}`,
      value: resource.resourceCode,
    })),
);
const functionActionItems = computed<ResourceActionItem[]>(() =>
  resourceList.value.flatMap((resource) =>
    (resource.actions ?? [])
      .filter((action) => action.status !== 0)
      .map((action) => ({
        action,
        key: functionActionKey(resource.resourceCode, action.actionCode),
        resource,
      })),
  ),
);
const functionActionByKey = computed(
  () => new Map(functionActionItems.value.map((item) => [item.key, item])),
);
const functionActionKeySet = computed(
  () => new Set(functionActionItems.value.map((item) => item.key)),
);
const authorizationTreeData = computed<AuthorizationTreeNode[]>(() =>
  resourceGroups.value.map((group) => ({
    children: (group.resources ?? []).map((resource) => ({
      children: (resource.actions ?? []).map((action) => ({
        disabled: action.status === 0,
        key: functionActionKey(resource.resourceCode, action.actionCode),
        title: actionTitle(action),
      })),
      disableCheckbox: true,
      key: `resource:${resource.resourceCode}`,
      selectable: false,
      title: resourceTitle(resource),
    })),
    disableCheckbox: true,
    key: `group:${group.resourceType}`,
    selectable: false,
    title: `${group.label} (${group.resources?.length ?? 0})`,
  })),
);
const selectedFunctionGrantCount = computed(
  () => selectedFunctionGrantKeys().length,
);
const dataScopeOptions = computed(() =>
  (authorizationOptions.value?.dataScopes ?? []).map(toSelectOption),
);
const dimensionOptions = computed(() =>
  orgDimensions.value.map((item) => ({
    label: item.dimensionName,
    value: item.dimensionCode,
  })),
);
const assignedOrgOptions = computed(
  () => orgOptionsMap.value[dataPolicyForm.dimensionCode] ?? [],
);
const fieldReadModeOptions = computed(() =>
  (authorizationOptions.value?.fieldReadModes ?? []).map(toSelectOption),
);
const fieldWriteModeOptions = computed(() =>
  (authorizationOptions.value?.fieldWriteModes ?? []).map(toSelectOption),
);
const maskStrategyOptions = computed(() =>
  (authorizationOptions.value?.maskStrategies ?? []).map(toSelectOption),
);
const dataPolicyActionOptions = computed(() =>
  actionOptionsForResource(dataPolicyForm.resourceCode),
);
const fieldPolicyFieldOptions = computed(() =>
  fieldOptionsForResource(fieldPolicyForm.resourceCode),
);
const previewActionOptions = computed(() =>
  actionOptionsForResource(previewForm.resourceCode),
);
const dataPolicyRows = computed(
  () => authorizationProfile.value?.dataPolicies ?? [],
);
const fieldPolicyRows = computed(
  () => authorizationProfile.value?.fieldPolicies ?? [],
);
const guardTemplateRows = computed(
  () => authorizationOptions.value?.guardTemplates ?? [],
);
const resourceGuardRows = computed(() =>
  resourceList.value
    .filter((resource) => (resource.guards ?? []).length > 0)
    .flatMap((resource) =>
      resource.guards.map((guard) => ({
        guard,
        resource,
      })),
    ),
);

const allDraftChecked = computed({
  get: () => columnDraft.value.every((item) => item.visible),
  set: (checked: boolean) => {
    columnDraft.value.forEach((item) => {
      item.visible = checked;
    });
  },
});

const baseColumns: Record<RoleColumnKey, NonNullable<TableProps['columns']>[number]> =
  {
    action: { align: 'center', fixed: 'right', key: 'action', title: '操作', width: 230 },
    createdAt: {
      align: 'center',
      dataIndex: 'createdAt',
      key: 'createdAt',
      title: '创建时间',
      width: 210,
    },
    description: {
      dataIndex: 'description',
      key: 'description',
      title: '描述',
      width: 260,
    },
    id: { align: 'center', dataIndex: 'id', key: 'id', title: '角色ID', width: 150 },
    roleCode: {
      align: 'center',
      dataIndex: 'roleCode',
      key: 'roleCode',
      title: '角色编码',
      width: 160,
    },
    roleName: {
      dataIndex: 'roleName',
      fixed: 'left',
      key: 'roleName',
      title: '角色名称',
      width: 180,
    },
    status: { align: 'center', dataIndex: 'status', key: 'status', title: '状态', width: 110 },
  };

const columns = computed<TableProps['columns']>(() =>
  columnSettings
    .filter((item) => item.visible)
    .map((item) => ({
      ...baseColumns[item.key],
      width: item.width,
    })),
);

function asRole(record: Record<string, any>) {
  return record as SystemRoleApi.SystemRole;
}

function statusValue(record?: Pick<SystemRoleApi.SystemRole, 'status'>) {
  return record?.status ?? 1;
}

function formatDate(value?: string) {
  return value ? value.replace('T', ' ') : '-';
}

function menuTypeLabel(type?: SystemMenuApi.MenuType) {
  const labels: Record<SystemMenuApi.MenuType, string> = {
    button: '权限点',
    catalog: '目录',
    embedded: '内嵌',
    link: '外链',
    menu: '菜单',
  };
  return labels[type ?? 'menu'];
}

function functionActionKey(resourceCode: string, actionCode: string) {
  return `authz:${resourceCode}:${actionCode}`;
}

function toSelectOption(option: SystemAuthorizationApi.Option) {
  return {
    label: option.label || option.code,
    value: option.code,
  };
}

function actionTitle(action: SystemAuthorizationApi.ActionNode) {
  const label = optionLabel(
    authorizationOptions.value?.functionActions,
    action.actionCode,
    action.description || action.actionCode,
  );
  return action.guardCodes?.length
    ? `${label} · ${action.guardCodes.join('/')}`
    : label;
}

function resourceTitle(resource: SystemAuthorizationApi.ResourceNode) {
  return `${resource.displayName || resource.resourceCode} · ${resource.resourceCode}`;
}

function optionLabel(
  options: SystemAuthorizationApi.Option[] | undefined,
  code: string | undefined,
  fallback?: string,
) {
  if (!code) return fallback || '-';
  return options?.find((item) => item.code === code)?.label || fallback || code;
}

function dataScopeLabel(code?: string) {
  return optionLabel(authorizationOptions.value?.dataScopes, code, code);
}

function fieldReadModeLabel(code?: string) {
  return optionLabel(authorizationOptions.value?.fieldReadModes, code, code);
}

function fieldWriteModeLabel(code?: string) {
  return optionLabel(authorizationOptions.value?.fieldWriteModes, code, code);
}

function maskStrategyLabel(code?: string) {
  return optionLabel(authorizationOptions.value?.maskStrategies, code, code || '-');
}

function grantEffectColor(effect?: string) {
  return effect === 'DENY' ? 'error' : 'success';
}

function findResource(resourceCode: string) {
  return resourceList.value.find((resource) => resource.resourceCode === resourceCode);
}

function actionOptionsForResource(resourceCode: string) {
  const resource = findResource(resourceCode);
  return (resource?.actions ?? [])
    .filter((action) => action.status !== 0)
    .map((action) => ({
      label: actionTitle(action),
      value: action.actionCode,
    }));
}

function fieldOptionsForResource(resourceCode: string) {
  const resource = findResource(resourceCode);
  return (resource?.fields ?? [])
    .filter((field) => field.status !== 0)
    .map((field) => ({
      label: field.fieldLabel || field.fieldKey,
      value: field.fieldKey,
    }));
}

function selectedFunctionGrantKeys() {
  const checkedKeys = Array.isArray(functionGrantCheckedKeys.value)
    ? functionGrantCheckedKeys.value
    : functionGrantCheckedKeys.value.checked;
  return checkedKeys
    .map((key) => String(key))
    .filter((key) => functionActionKeySet.value.has(key));
}

function applyAuthorizationProfile(
  profile: SystemAuthorizationApi.RoleAuthorizationProfile,
) {
  authorizationProfile.value = profile;
  const grantIds: Record<string, string> = {};
  for (const grant of profile.functionGrants ?? []) {
    if (grant.effect !== 'ALLOW' || grant.status === 0) {
      continue;
    }
    const key = functionActionKey(grant.resourceCode, grant.actionCode);
    if (functionActionKeySet.value.has(key) && grant.id) {
      grantIds[key] = grant.id;
    }
  }
  originalFunctionGrantIds.value = grantIds;
  functionGrantCheckedKeys.value = Object.keys(grantIds);
}

function resetAuthorizationState() {
  authorizationTree.value = undefined;
  authorizationOptions.value = undefined;
  authorizationProfile.value = undefined;
  functionGrantCheckedKeys.value = [];
  originalFunctionGrantIds.value = {};
  previewResult.value = undefined;
  resetAuthorizationForms();
}

function resetAuthorizationForms() {
  dataPolicyForm.resourceCode = '';
  dataPolicyForm.actionCode = '';
  dataPolicyForm.dimensionCode = 'ADMIN';
  dataPolicyForm.orgUnitIds = [];
  dataPolicyForm.scopeType = '';
  fieldPolicyForm.resourceCode = '';
  fieldPolicyForm.fieldKey = '';
  fieldPolicyForm.readMode = '';
  fieldPolicyForm.writeMode = '';
  fieldPolicyForm.maskStrategy = '';
  previewForm.resourceCode = '';
  previewForm.actionCode = '';
  previewForm.businessObjectId = '';
  previewForm.userId = '';
}

function resetAuthorizationQuickForms() {
  const firstResource = resourceList.value[0];
  dataPolicyForm.resourceCode = firstResource?.resourceCode ?? '';
  dataPolicyForm.actionCode = firstResource?.actions?.[0]?.actionCode ?? '';
  dataPolicyForm.dimensionCode = orgDimensions.value[0]?.dimensionCode ?? 'ADMIN';
  dataPolicyForm.orgUnitIds = [];
  dataPolicyForm.scopeType =
    authorizationOptions.value?.dataScopes?.[0]?.code ?? 'SELF';

  const firstFieldResource = resourceList.value.find(
    (resource) => (resource.fields ?? []).length > 0,
  );
  fieldPolicyForm.resourceCode = firstFieldResource?.resourceCode ?? '';
  fieldPolicyForm.fieldKey = firstFieldResource?.fields?.[0]?.fieldKey ?? '';
  fieldPolicyForm.readMode =
    authorizationOptions.value?.fieldReadModes?.[0]?.code ?? 'VISIBLE';
  fieldPolicyForm.writeMode =
    authorizationOptions.value?.fieldWriteModes?.[0]?.code ?? 'EDITABLE';
  fieldPolicyForm.maskStrategy =
    authorizationOptions.value?.maskStrategies?.[0]?.code ?? '';

  previewForm.resourceCode = firstResource?.resourceCode ?? '';
  previewForm.actionCode = firstResource?.actions?.[0]?.actionCode ?? '';
  previewForm.businessObjectId = '';
  previewForm.userId = '';
}

function changeDataPolicyResource(resourceCode: string) {
  dataPolicyForm.resourceCode = resourceCode;
  dataPolicyForm.actionCode = actionOptionsForResource(resourceCode)[0]?.value ?? '';
}

async function changeDataPolicyDimension(dimensionCode: string) {
  dataPolicyForm.dimensionCode = dimensionCode;
  dataPolicyForm.orgUnitIds = [];
  if (dataPolicyForm.scopeType === 'ASSIGNED_ORGS') {
    await ensureOrgOptions(dimensionCode);
  }
}

async function changeDataPolicyScope(scopeType: string) {
  dataPolicyForm.scopeType = scopeType;
  if (scopeType !== 'ASSIGNED_ORGS') {
    dataPolicyForm.orgUnitIds = [];
    return;
  }
  await ensureOrgOptions(dataPolicyForm.dimensionCode);
}

function changeFieldPolicyResource(resourceCode: string) {
  fieldPolicyForm.resourceCode = resourceCode;
  fieldPolicyForm.fieldKey = fieldOptionsForResource(resourceCode)[0]?.value ?? '';
}

function changePreviewResource(resourceCode: string) {
  previewForm.resourceCode = resourceCode;
  previewForm.actionCode = actionOptionsForResource(resourceCode)[0]?.value ?? '';
  previewResult.value = undefined;
}

function buildMenuTree(list: SystemMenuApi.SystemMenu[], readOnly = false) {
  const nodeMap = new Map<string, MenuTreeNode>();
  list.forEach((menu) => {
    nodeMap.set(menu.id, {
      children: [],
      disabled: readOnly,
      key: menu.id,
      selectable: false,
      sortOrder: menu.sortOrder ?? 100,
      title: `${menu.menuName || menu.menuKey} (${menuTypeLabel(menu.menuType)})`,
    });
  });

  const roots: MenuTreeNode[] = [];
  list.forEach((menu) => {
    const node = nodeMap.get(menu.id);
    if (!node) {
      return;
    }
    if (menu.parentId && nodeMap.has(menu.parentId)) {
      nodeMap.get(menu.parentId)?.children?.push(node);
    } else {
      roots.push(node);
    }
  });

  const sortNodes = (nodes: MenuTreeNode[]) => {
    nodes.sort((a, b) => (a.sortOrder ?? 100) - (b.sortOrder ?? 100));
    nodes.forEach((node) => {
      if (node.children && node.children.length > 0) {
        sortNodes(node.children);
      } else {
        delete node.children;
      }
      delete node.sortOrder;
    });
  };
  sortNodes(roots);
  return roots;
}

async function loadMenusForAuthorization(force = false) {
  if (!force && menus.value.length > 0) {
    return;
  }
  if (!canQueryMenus.value || (!canCreate.value && !canUpdate.value)) {
    return;
  }
  loadingMenus.value = true;
  try {
    menus.value = await getMenuList();
  } finally {
    loadingMenus.value = false;
  }
}

async function loadAuthorizationDimensions() {
  if (!canQueryOrg.value) {
    orgDimensions.value = [];
    return;
  }
  if (orgDimensions.value.length > 0) {
    return;
  }
  orgDimensions.value = await getOrgDimensions();
}

async function loadAuthorizationWorkbench(roleId: string) {
  if (!canQueryAuthz.value) {
    return;
  }
  authorizationLoading.value = true;
  try {
    const [tree, options, profile] = await Promise.all([
      getAuthorizationResourceTree(),
      getAuthorizationAdminOptions(),
      getRoleAuthorizationProfile(roleId),
      loadAuthorizationDimensions(),
    ]);
    authorizationTree.value = tree;
    authorizationOptions.value = options;
    applyAuthorizationProfile(profile);
    resetAuthorizationQuickForms();
  } catch {
    message.warning('角色授权配置加载失败');
  } finally {
    authorizationLoading.value = false;
  }
}

function flattenOrgTree(list: SystemOrgApi.OrgTreeNode[]) {
  const result: SystemOrgApi.OrgTreeNode[] = [];
  const walk = (nodes: SystemOrgApi.OrgTreeNode[]) => {
    nodes.forEach((node) => {
      result.push(node);
      if (node.children?.length) {
        walk(node.children);
      }
    });
  };
  walk(list);
  return result;
}

async function ensureOrgOptions(dimensionCode: string) {
  if (!canQueryOrg.value || !dimensionCode || orgOptionsMap.value[dimensionCode]) {
    return;
  }
  const tree = await getOrgTree(dimensionCode);
  orgOptionsMap.value = {
    ...orgOptionsMap.value,
    [dimensionCode]: flattenOrgTree(tree).map((item) => ({
      label: `${item.unitName} (${item.unitCode})`,
      value: item.id,
    })),
  };
}

async function refreshAuthorizationProfile(roleId = editingRole.value?.id) {
  if (!roleId || !canQueryAuthz.value) {
    return;
  }
  const profile = await getRoleAuthorizationProfile(roleId);
  applyAuthorizationProfile(profile);
}

async function refreshRoleProjection(roleId = editingRole.value?.id) {
  if (!roleId) {
    return;
  }
  const detail = await getRoleDetail(roleId);
  if (editingRole.value?.id === roleId) {
    editingRole.value = detail;
    formModel.visibleMenuIds = detail.menuIds ?? [];
  }
  if (detailRole.value?.id === roleId) {
    detailRole.value = detail;
  }
}

async function loadRoles(page = pagination.current) {
  if (!canQuery.value) {
    roles.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const roleViewQuery = selectedRoleView.value.query;
    const result = await getRolePage({
      createdEnd: queryForm.createdRange?.[1]
        ?.endOf('day')
        .format('YYYY-MM-DDTHH:mm:ss'),
      createdStart: queryForm.createdRange?.[0]
        ?.startOf('day')
        .format('YYYY-MM-DDTHH:mm:ss'),
      keyword: queryForm.keyword?.trim() || undefined,
      page,
      roleCode: roleViewQuery.roleCode || queryForm.roleCode?.trim() || undefined,
      roleName: queryForm.roleName?.trim() || undefined,
      size: pagination.pageSize,
      status: roleViewQuery.status ?? queryForm.status,
    });
    roles.value = result.items;
    pagination.current = page;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  queryForm.createdRange = undefined;
  queryForm.keyword = '';
  queryForm.roleCode = '';
  queryForm.roleName = '';
  queryForm.status = undefined;
  selectedRoleViewKey.value = 'all';
  loadRoles(1);
}

async function handleToolbarSearch() {
  await loadRoles(1);
  queryHidden.value = true;
}

function selectRoleView(keys: Array<number | string>) {
  const key = keys[0];
  if (!key || !(key in roleViewPresets)) {
    return;
  }
  selectedRoleViewKey.value = key as RoleViewKey;
  loadRoles(1);
}

function resetForm() {
  editingRole.value = undefined;
  permissionOnly.value = false;
  activeAuthorizationTab.value = canQueryAuthz.value ? 'function' : 'menu';
  formModel.description = '';
  formModel.roleCode = '';
  formModel.roleName = '';
  formModel.status = 1;
  formModel.visibleMenuIds = [];
  resetAuthorizationState();
}

async function openCreate() {
  resetForm();
  formOpen.value = true;
}

async function openEdit(record: SystemRoleApi.SystemRole, onlyPermissions = false) {
  resetForm();
  permissionOnly.value = onlyPermissions;
  await Promise.all([
    loadMenusForAuthorization(true),
    loadAuthorizationWorkbench(record.id),
  ]);
  const detail = await getRoleDetail(record.id);
  editingRole.value = detail;
  formModel.description = detail.description ?? '';
  formModel.roleCode = detail.roleCode;
  formModel.roleName = detail.roleName;
  formModel.status = statusValue(detail);
  formModel.visibleMenuIds = detail.menuIds ?? [];
  formOpen.value = true;
}

async function openDetail(record: SystemRoleApi.SystemRole) {
  await loadMenusForAuthorization(true);
  detailRole.value = await getRoleDetail(record.id);
  detailOpen.value = true;
}

function cloneColumnSettings(source = columnSettings) {
  return source.map((item) => ({ ...item }));
}

function syncColumnDraft(open: boolean) {
  columnSettingOpen.value = open;
  if (open) {
    columnDraft.value = cloneColumnSettings();
  }
}

function restoreColumnSettings() {
  columnDraft.value = defaultColumnSettings.map((item) => ({ ...item }));
}

function cancelColumnSettings() {
  columnSettingOpen.value = false;
}

function confirmColumnSettings() {
  columnSettings.splice(0, columnSettings.length, ...cloneColumnSettings(columnDraft.value));
  tableKey.value += 1;
  columnSettingOpen.value = false;
}

async function syncFunctionGrants(roleId: string) {
  if (!canManageAuthz.value || !canQueryAuthz.value) {
    return;
  }
  const desiredKeys = new Set(selectedFunctionGrantKeys());
  const existingIds = originalFunctionGrantIds.value;
  const additions = [...desiredKeys].filter((key) => !existingIds[key]);
  const removals = Object.entries(existingIds).filter(([key]) => !desiredKeys.has(key));

  if (additions.length > 0 && !canCreateAuthz.value) {
    message.warning('当前账号没有新增功能授权的权限');
    return;
  }
  if (removals.length > 0 && !canDeleteAuthz.value) {
    message.warning('当前账号没有删除功能授权的权限');
    return;
  }

  await Promise.all([
    ...additions.map((key) => {
      const item = functionActionByKey.value.get(key);
      if (!item) return undefined;
      return saveAuthorizationGrant({
        actionCode: item.action.actionCode,
        description: `${item.resource.displayName || item.resource.resourceCode} / ${actionTitle(item.action)}`,
        effect: 'ALLOW',
        resourceCode: item.resource.resourceCode,
        status: 1,
        subjectId: roleId,
        subjectType: 'ROLE',
      });
    }),
    ...removals.map(([, id]) => deleteAuthorizationGrant(id)),
  ].filter(Boolean));

  if (additions.length > 0 || removals.length > 0) {
    await Promise.all([
      refreshAuthorizationProfile(roleId),
      refreshRoleProjection(roleId),
    ]);
  }
}

async function addDataPolicy() {
  if (!editingRole.value) return;
  if (!canCreateDataPolicy.value) {
    message.warning('当前账号没有新增授权配置的权限');
    return;
  }
  if (
    !dataPolicyForm.resourceCode ||
    !dataPolicyForm.actionCode ||
    !dataPolicyForm.scopeType ||
    !dataPolicyForm.dimensionCode
  ) {
    message.warning('请选择数据范围配置');
    return;
  }
  if (
    dataPolicyForm.scopeType === 'ASSIGNED_ORGS' &&
    dataPolicyForm.orgUnitIds.length === 0
  ) {
    message.warning('指定组织范围必须选择组织');
    return;
  }
  const payload: SystemDataPolicyApi.SaveDataPolicyParams = {
    actionCode: dataPolicyForm.actionCode,
    combineMode: 'OR',
    description: `数据范围：${dataScopeLabel(dataPolicyForm.scopeType)}`,
    dimensions: [
      {
        dimensionCode: dataPolicyForm.dimensionCode,
        orgUnitIds:
          dataPolicyForm.scopeType === 'ASSIGNED_ORGS'
            ? dataPolicyForm.orgUnitIds
            : [],
        scopeType: dataPolicyForm.scopeType,
        sortOrder: 0,
      },
    ],
    effect: 'ALLOW',
    resourceCode: dataPolicyForm.resourceCode,
    roleId: editingRole.value.id,
    status: 1,
  };
  await createDataPolicy(payload);
  message.success('数据范围已添加');
  await refreshAuthorizationProfile();
}

async function removeDataPolicy(policy: SystemAuthorizationApi.DataPolicy) {
  if (!policy.id) return;
  if (!canDeleteDataPolicy.value) {
    message.warning('当前账号没有删除授权配置的权限');
    return;
  }
  await deleteDataPolicy(policy.id);
  message.success('数据范围已删除');
  await refreshAuthorizationProfile();
}

async function addFieldPolicy() {
  if (!editingRole.value) return;
  if (!canCreateAuthz.value) {
    message.warning('当前账号没有新增授权配置的权限');
    return;
  }
  if (
    !fieldPolicyForm.resourceCode ||
    !fieldPolicyForm.fieldKey ||
    !fieldPolicyForm.readMode ||
    !fieldPolicyForm.writeMode
  ) {
    message.warning('请选择字段规则配置');
    return;
  }
  await saveAuthorizationFieldPolicy({
    description: `字段规则：${fieldReadModeLabel(fieldPolicyForm.readMode)} / ${fieldWriteModeLabel(fieldPolicyForm.writeMode)}`,
    effect: 'ALLOW',
    fieldKey: fieldPolicyForm.fieldKey,
    maskStrategy: fieldPolicyForm.maskStrategy || undefined,
    readMode: fieldPolicyForm.readMode,
    resourceCode: fieldPolicyForm.resourceCode,
    status: 1,
    subjectId: editingRole.value.id,
    subjectType: 'ROLE',
    writeMode: fieldPolicyForm.writeMode,
  });
  message.success('字段规则已添加');
  await refreshAuthorizationProfile();
}

async function removeFieldPolicy(policy: SystemAuthorizationApi.FieldPolicy) {
  if (!policy.id) return;
  if (!canDeleteAuthz.value) {
    message.warning('当前账号没有删除授权配置的权限');
    return;
  }
  await deleteAuthorizationFieldPolicy(policy.id);
  message.success('字段规则已删除');
  await refreshAuthorizationProfile();
}

async function toggleGuardTemplate(
  template: SystemAuthorizationApi.GuardTemplate,
  checked: boolean,
) {
  if (!template.id) return;
  if (!canUpdateAuthz.value) {
    message.warning('当前账号没有更新业务规则的权限');
    return;
  }
  await updateAuthorizationGuardTemplateStatus(template.id, checked ? 1 : 0);
  message.success('业务规则状态已更新');
  if (authorizationOptions.value) {
    authorizationOptions.value.guardTemplates =
      authorizationOptions.value.guardTemplates.map((item) =>
        item.id === template.id ? { ...item, status: checked ? 1 : 0 } : item,
      );
  }
}

async function runDecisionPreview() {
  if (!canCreateAuthz.value) {
    message.warning('当前账号没有决策预览权限');
    return;
  }
  if (!previewForm.userId.trim() || !previewForm.resourceCode || !previewForm.actionCode) {
    message.warning('请输入预览用户并选择资源动作');
    return;
  }
  const resource = findResource(previewForm.resourceCode);
  previewLoading.value = true;
  previewResult.value = undefined;
  try {
    previewResult.value = await previewAuthorizationDecision({
      actionCode: previewForm.actionCode,
      businessObjectId: previewForm.businessObjectId.trim() || undefined,
      fieldKeys: (resource?.fields ?? []).map((field) => field.fieldKey),
      ownerService: resource?.ownerService,
      resourceCode: previewForm.resourceCode,
      userId: previewForm.userId.trim(),
    });
  } finally {
    previewLoading.value = false;
  }
}

function validateForm() {
  if (!editingRole.value && !formModel.roleCode.trim()) {
    message.warning('请输入角色编码');
    return false;
  }
  if (!formModel.roleName.trim()) {
    message.warning('请输入角色名称');
    return false;
  }
  return true;
}

async function submitForm() {
  if (!canSaveRole.value) {
    message.warning('当前账号没有保存角色的权限');
    return;
  }
  if (!validateForm()) {
    return;
  }
  saving.value = true;
  try {
    if (!editingRole.value && (await roleCodeExists(formModel.roleCode.trim()))) {
      message.warning('角色编码已存在');
      return;
    }
    const payload: SystemRoleApi.SaveRoleParams = {
      description: formModel.description?.trim() || undefined,
      roleName: formModel.roleName.trim(),
      status: formModel.status,
    };
    if (!editingRole.value) {
      payload.roleCode = formModel.roleCode.trim();
    }

    if (editingRole.value) {
      await updateRole(editingRole.value.id, payload);
      await syncFunctionGrants(editingRole.value.id);
      message.success(permissionOnly.value ? '角色授权已更新' : '角色已更新');
    } else {
      await createRole(payload);
      message.success('角色已创建');
    }

    formOpen.value = false;
    await loadRoles();
  } finally {
    saving.value = false;
  }
}

function changeStatus(record: SystemRoleApi.SystemRole, checked: boolean) {
  const nextStatus = checked ? 1 : 0;
  Modal.confirm({
    content: `确认${checked ? '启用' : '禁用'}角色 ${record.roleName}？`,
    onOk: async () => {
      await updateRoleStatus(record.id, nextStatus);
      message.success('状态已更新');
      await loadRoles();
    },
    onCancel: () => {
      tableKey.value += 1;
    },
    title: '切换角色状态',
  });
}

async function removeRole(record: SystemRoleApi.SystemRole) {
  await deleteRole(record.id);
  message.success('角色已删除');
  const nextPage =
    roles.value.length === 1 && pagination.current > 1
      ? pagination.current - 1
      : pagination.current;
  await loadRoles(nextPage);
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRoles(page);
}

function onPageSizeChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRoles(page);
}

function toggleFullscreen() {
  blockFullscreen.value = !blockFullscreen.value;
}

onMounted(() => {
  loadRoles(1);
});
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold
      class="role-page"
      pattern="master-detail"
      :fullscreen="blockFullscreen"
      :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }"
    >
      <section v-show="!queryHidden" class="query-panel">
        <div class="query-grid" :class="{ collapsed }">
          <FormItem label="角色名称">
            <Input
              v-model:value="queryForm.roleName"
              allow-clear
              placeholder="请输入"
              @press-enter="loadRoles(1)"
            />
          </FormItem>
          <FormItem label="角色编码">
            <Input
              v-model:value="queryForm.roleCode"
              allow-clear
              placeholder="请输入"
              @press-enter="loadRoles(1)"
            />
          </FormItem>
          <FormItem v-if="!collapsed" label="状态">
            <Select
              v-model:value="queryForm.status"
              allow-clear
              placeholder="请选择"
              :options="[
                { label: '启用', value: 1 },
                { label: '禁用', value: 0 },
              ]"
            />
          </FormItem>
          <FormItem v-if="!collapsed" label="关键词">
            <Input
              v-model:value="queryForm.keyword"
              allow-clear
              placeholder="编码/名称/描述"
              @press-enter="loadRoles(1)"
            />
          </FormItem>
          <FormItem v-if="!collapsed" label="创建时间">
            <RangePicker
              v-model:value="queryForm.createdRange"
              class="w-full"
              :placeholder="['开始日期', '结束日期']"
            />
          </FormItem>
          <div class="query-actions">
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Button v-if="canQuery" type="primary" @click="loadRoles(1)">搜索</Button>
            <Button type="link" @click="collapsed = !collapsed">
              {{ collapsed ? '展开' : '收起' }}
              <IconifyIcon
                :icon="collapsed ? ERP_TOOLBAR_ICONS.expand : ERP_TOOLBAR_ICONS.collapse"
                class="ml-1 size-4"
              />
            </Button>
          </div>
        </div>
      </section>

      <section class="role-workbench">
        <aside class="role-tree-panel">
          <div class="role-tree-header">
            <div>
              <h3>角色视图</h3>
              <span>{{ selectedRoleView.label }}</span>
            </div>
            <Tooltip title="重置为全部角色">
              <Button
                :disabled="selectedRoleViewKey === 'all'"
                shape="circle"
                size="small"
                @click="selectRoleView(['all'])"
              >
                <IconifyIcon :icon="ERP_TOOLBAR_ICONS.reset" class="size-3.5" />
              </Button>
            </Tooltip>
          </div>

          <Tree
            :selected-keys="selectedRoleViewKeys"
            :tree-data="roleViewTree"
            block-node
            default-expand-all
            @select="selectRoleView"
          />

          <div class="role-tree-hint">
            {{ selectedRoleView.description }}
          </div>
        </aside>

        <section class="list-panel">
          <div class="list-header">
            <div class="list-title">
              <h2>角色列表</h2>
              <Tag color="blue">{{ selectedRoleView.label }}</Tag>
              <Button v-if="queryHidden" type="link" @click="queryHidden = false">
                展开搜索
              </Button>
            </div>
            <Space :size="8">
              <Button v-if="canCreate" type="primary" @click="openCreate">
                <Plus class="size-4" />
                新增角色
              </Button>
              <Tooltip v-if="canQuery" title="查询并隐藏搜索栏">
                <Button shape="circle" type="primary" @click="handleToolbarSearch">
                  <IconifyIcon :icon="ERP_TOOLBAR_ICONS.search" class="size-4" />
                </Button>
              </Tooltip>
              <Tooltip v-if="canQuery" title="刷新">
                <Button shape="circle" @click="loadRoles()">
                  <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
                </Button>
              </Tooltip>
              <Tooltip :title="blockFullscreen ? '还原' : '全屏'">
                <Button shape="circle" @click="toggleFullscreen">
                  <IconifyIcon
                    :icon="
                      blockFullscreen
                        ? ERP_TOOLBAR_ICONS.fullscreenExit
                        : ERP_TOOLBAR_ICONS.fullscreen
                    "
                    class="size-4"
                  />
                </Button>
              </Tooltip>
              <Popover
                :open="columnSettingOpen"
                overlay-class-name="role-column-popover"
                placement="bottomRight"
                trigger="click"
                @open-change="syncColumnDraft"
              >
                <template #content>
                  <div class="column-settings">
                    <Checkbox v-model:checked="allDraftChecked" class="column-check-all">
                      全部
                    </Checkbox>
                    <div class="column-setting-list">
                      <div
                        v-for="item in columnDraft"
                        :key="item.key"
                        class="column-setting-item"
                      >
                        <Checkbox v-model:checked="item.visible" />
                        <IconifyIcon :icon="ERP_TOOLBAR_ICONS.drag" class="drag-icon" />
                        <span class="column-setting-title">{{ item.title }}</span>
                      </div>
                    </div>
                    <div class="column-setting-footer">
                      <Button type="link" @click="restoreColumnSettings">恢复默认</Button>
                      <Space>
                        <Button type="text" @click="cancelColumnSettings">取消</Button>
                        <Button type="link" @click="confirmColumnSettings">确认</Button>
                      </Space>
                    </div>
                  </div>
                </template>
                <Tooltip title="列设置">
                  <Button
                    :class="{ 'is-active': columnSettingOpen }"
                    class="column-setting-trigger"
                    shape="circle"
                  >
                    <IconifyIcon :icon="ERP_TOOLBAR_ICONS.columnSettings" class="size-4" />
                  </Button>
                </Tooltip>
              </Popover>
            </Space>
          </div>

          <div class="table-frame">
            <Table
              :key="tableKey"
              :columns="columns"
              :data-source="roles"
              :loading="loading"
              :pagination="false"
              :scroll="{ x: 'max-content' }"
              :sticky="{ offsetScroll: 0 }"
              bordered
              row-key="id"
              table-layout="fixed"
              size="small"
            >
              <template #emptyText>
                <Empty description="暂无数据" />
              </template>

              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'roleName'">
                  <div class="role-name-cell">
                    <IconifyIcon icon="lucide:shield-user" class="size-4" />
                    <span>{{ record.roleName }}</span>
                  </div>
                </template>

                <template v-else-if="column.key === 'status'">
                  <Switch
                    v-if="canUpdate"
                    :checked="statusValue(asRole(record)) === 1"
                    checked-children="启用"
                    un-checked-children="禁用"
                    @change="(checked) => changeStatus(asRole(record), checked as boolean)"
                  />
                  <Tag v-else :color="statusValue(asRole(record)) === 1 ? 'success' : 'error'">
                    {{ statusValue(asRole(record)) === 1 ? '启用' : '禁用' }}
                  </Tag>
                </template>

                <template v-else-if="column.key === 'description'">
                  {{ record.description || '-' }}
                </template>

                <template v-else-if="column.key === 'createdAt'">
                  {{ formatDate(record.createdAt) }}
                </template>

                <template v-else-if="column.key === 'action'">
                  <Space>
                    <Button size="small" type="link" @click="openDetail(asRole(record))">
                      详情
                    </Button>
                    <Button
                      v-if="canUpdate"
                      size="small"
                      type="link"
                      @click="openEdit(asRole(record))"
                    >
                      编辑
                    </Button>
                    <Button
                      v-if="canOpenAuthorization"
                      size="small"
                      type="link"
                      @click="openEdit(asRole(record), true)"
                    >
                      授权
                    </Button>
                    <Popconfirm
                      v-if="canDelete"
                      title="确认删除该角色？"
                      ok-text="删除"
                      cancel-text="取消"
                      @confirm="removeRole(asRole(record))"
                    >
                      <Button danger size="small" type="link">删除</Button>
                    </Popconfirm>
                  </Space>
                </template>
              </template>
            </Table>
          </div>

          <div class="table-footer">
            <div class="table-total">共 {{ pagination.total }} 条记录</div>
            <Pagination
              v-model:current="pagination.current"
              v-model:page-size="pagination.pageSize"
              :page-size-options="['10', '20', '50', '100']"
              size="small"
              :total="pagination.total"
              show-less-items
              show-size-changer
              @change="onPageChange"
              @show-size-change="onPageSizeChange"
            />
          </div>
        </section>
      </section>
    </BusinessPageScaffold>

    <Drawer
      v-model:open="formOpen"
      :title="permissionOnly ? '角色授权' : editingRole ? '编辑角色' : '新增角色'"
      width="760"
      :destroy-on-close="false"
    >
      <Form :model="formModel" class="role-form" layout="horizontal">
        <div class="form-grid">
          <FormItem label="角色编码" :required="!editingRole">
            <Input
              v-model:value="formModel.roleCode"
              allow-clear
              :disabled="!!editingRole || permissionOnly"
              placeholder="如 ADMIN"
            />
          </FormItem>
          <FormItem label="角色名称" required>
            <Input
              v-model:value="formModel.roleName"
              allow-clear
              :disabled="permissionOnly"
              placeholder="请输入"
            />
          </FormItem>
          <FormItem label="状态">
            <RadioGroup
              v-model:value="formModel.status"
              button-style="solid"
              :disabled="permissionOnly"
            >
              <Radio :value="1">启用</Radio>
              <Radio :value="0">禁用</Radio>
            </RadioGroup>
          </FormItem>
          <FormItem class="form-full" label="描述">
            <Textarea
              v-model:value="formModel.description"
              :disabled="permissionOnly"
              :rows="3"
              allow-clear
              placeholder="请输入"
            />
          </FormItem>
        </div>

        <Tabs
          v-if="(canQueryMenus && editingRole) || canQueryAuthz"
          v-model:active-key="activeAuthorizationTab"
          class="authorization-tabs"
          size="small"
        >
          <TabPane v-if="canQueryMenus && editingRole" key="menu" tab="菜单可见性">
            <div class="authorization-toolbar">
              <span>由功能授权自动推导</span>
              <Tag color="blue">{{ visibleMenuCount }} 项</Tag>
            </div>
            <div class="permission-panel">
              <Tree
                :checked-keys="formModel.visibleMenuIds"
                :check-strictly="true"
                :tree-data="menuTree"
                block-node
                checkable
                default-expand-all
                :loading="loadingMenus"
              />
            </div>
          </TabPane>

          <TabPane
            v-if="canQueryAuthz"
            key="function"
            tab="功能授权"
          >
            <div v-if="editingRole" class="authorization-tab-body">
              <div class="authorization-toolbar">
                <span>资源动作</span>
                <Tag color="blue">已选 {{ selectedFunctionGrantCount }}</Tag>
              </div>
              <div class="permission-panel">
                <Tree
                  v-model:checkedKeys="functionGrantCheckedKeys"
                  :check-strictly="true"
                  :tree-data="authorizationTreeData"
                  block-node
                  checkable
                  default-expand-all
                  :loading="authorizationLoading"
                />
              </div>
            </div>
            <Empty v-else description="保存角色后配置功能授权" />
          </TabPane>

          <TabPane v-if="canQueryAuthz" key="data" :disabled="!editingRole" tab="数据范围">
            <div v-if="editingRole" class="authorization-tab-body">
              <div class="authorization-inline-form">
                <Select
                  v-model:value="dataPolicyForm.resourceCode"
                  class="auth-resource-select"
                  :options="resourceOptions"
                  placeholder="业务资源"
                  show-search
                  @change="(value) => changeDataPolicyResource(String(value))"
                />
                <Select
                  v-model:value="dataPolicyForm.actionCode"
                  class="auth-action-select"
                  :options="dataPolicyActionOptions"
                  placeholder="动作"
                />
                <Select
                  v-model:value="dataPolicyForm.scopeType"
                  class="auth-mode-select"
                  :options="dataScopeOptions"
                  placeholder="数据范围"
                  @change="(value) => changeDataPolicyScope(String(value))"
                />
                <Select
                  v-model:value="dataPolicyForm.dimensionCode"
                  class="auth-mode-select"
                  :disabled="!canQueryOrg"
                  :options="dimensionOptions"
                  placeholder="组织维度"
                  @change="(value) => changeDataPolicyDimension(String(value))"
                />
                <Select
                  v-if="dataPolicyForm.scopeType === 'ASSIGNED_ORGS'"
                  v-model:value="dataPolicyForm.orgUnitIds"
                  class="auth-org-select"
                  :disabled="!canQueryOrg"
                  mode="multiple"
                  :options="assignedOrgOptions"
                  placeholder="指定组织"
                  show-search
                />
                <Button
                  :disabled="!canCreateDataPolicy"
                  type="primary"
                  @click="addDataPolicy"
                >
                  添加
                </Button>
              </div>

              <div v-if="dataPolicyRows.length > 0" class="authorization-list">
                <div
                  v-for="policy in dataPolicyRows"
                  :key="policy.id || `${policy.resourceCode}:${policy.actionCode}`"
                  class="authorization-list-item"
                >
                  <div>
                    <div class="authorization-item-title">
                      {{ policy.resourceCode }} / {{ policy.actionCode }}
                    </div>
                    <Space wrap :size="4">
                      <Tag :color="grantEffectColor(policy.effect)">{{ policy.effect }}</Tag>
                      <Tag v-for="dimension in policy.dimensions" :key="dimension.id || dimension.scopeType">
                        {{ dimension.dimensionCode }} · {{ dataScopeLabel(dimension.scopeType) }}
                      </Tag>
                    </Space>
                  </div>
                  <Button
                    v-if="canDeleteDataPolicy"
                    danger
                    size="small"
                    type="link"
                    @click="removeDataPolicy(policy)"
                  >
                    删除
                  </Button>
                </div>
              </div>
              <Empty v-else description="暂无数据范围" />
            </div>
            <Empty v-else description="保存角色后配置数据范围" />
          </TabPane>

          <TabPane v-if="canQueryAuthz" key="field" :disabled="!editingRole" tab="字段规则">
            <div v-if="editingRole" class="authorization-tab-body">
              <div class="authorization-inline-form">
                <Select
                  v-model:value="fieldPolicyForm.resourceCode"
                  class="auth-resource-select"
                  :options="fieldResourceOptions"
                  placeholder="业务资源"
                  show-search
                  @change="(value) => changeFieldPolicyResource(String(value))"
                />
                <Select
                  v-model:value="fieldPolicyForm.fieldKey"
                  class="auth-field-select"
                  :options="fieldPolicyFieldOptions"
                  placeholder="字段"
                />
                <Select
                  v-model:value="fieldPolicyForm.readMode"
                  class="auth-mode-select"
                  :options="fieldReadModeOptions"
                  placeholder="读取"
                />
                <Select
                  v-model:value="fieldPolicyForm.writeMode"
                  class="auth-mode-select"
                  :options="fieldWriteModeOptions"
                  placeholder="写入"
                />
                <Select
                  v-model:value="fieldPolicyForm.maskStrategy"
                  class="auth-mode-select"
                  allow-clear
                  :options="maskStrategyOptions"
                  placeholder="掩码"
                />
                <Button
                  :disabled="!canCreateAuthz"
                  type="primary"
                  @click="addFieldPolicy"
                >
                  添加
                </Button>
              </div>

              <div v-if="fieldPolicyRows.length > 0" class="authorization-list">
                <div
                  v-for="policy in fieldPolicyRows"
                  :key="policy.id || `${policy.resourceCode}:${policy.fieldKey}`"
                  class="authorization-list-item"
                >
                  <div>
                    <div class="authorization-item-title">
                      {{ policy.resourceCode }} / {{ policy.fieldKey }}
                    </div>
                    <Space wrap :size="4">
                      <Tag :color="grantEffectColor(policy.effect)">{{ policy.effect }}</Tag>
                      <Tag>{{ fieldReadModeLabel(policy.readMode) }}</Tag>
                      <Tag>{{ fieldWriteModeLabel(policy.writeMode) }}</Tag>
                      <Tag v-if="policy.maskStrategy">
                        {{ maskStrategyLabel(policy.maskStrategy) }}
                      </Tag>
                    </Space>
                  </div>
                  <Button
                    v-if="canDeleteAuthz"
                    danger
                    size="small"
                    type="link"
                    @click="removeFieldPolicy(policy)"
                  >
                    删除
                  </Button>
                </div>
              </div>
              <Empty v-else description="暂无字段规则" />
            </div>
            <Empty v-else description="保存角色后配置字段规则" />
          </TabPane>

          <TabPane v-if="canQueryAuthz" key="guard" :disabled="!editingRole" tab="业务规则">
            <div v-if="editingRole" class="authorization-tab-body">
              <div class="authorization-section-title">模板状态</div>
              <div v-if="guardTemplateRows.length > 0" class="authorization-list">
                <div
                  v-for="template in guardTemplateRows"
                  :key="template.id || template.guardCode"
                  class="authorization-list-item"
                >
                  <div>
                    <div class="authorization-item-title">{{ template.guardCode }}</div>
                    <p>{{ template.description || '-' }}</p>
                    <Space wrap :size="4">
                      <Tag>{{ template.ownerService || 'service-auth' }}</Tag>
                      <Tag v-if="template.supportedResourceTypes">
                        {{ template.supportedResourceTypes }}
                      </Tag>
                    </Space>
                  </div>
                  <Switch
                    :checked="template.status !== 0"
                    :disabled="!canUpdateAuthz"
                    checked-children="启用"
                    un-checked-children="停用"
                    @change="(checked) => toggleGuardTemplate(template, checked as boolean)"
                  />
                </div>
              </div>
              <Empty v-else description="暂无业务规则模板" />

              <div class="authorization-section-title">资源规则</div>
              <div v-if="resourceGuardRows.length > 0" class="authorization-list">
                <div
                  v-for="item in resourceGuardRows"
                  :key="`${item.resource.resourceCode}:${item.guard.guardCode}`"
                  class="authorization-list-item"
                >
                  <div>
                    <div class="authorization-item-title">
                      {{ item.resource.displayName || item.resource.resourceCode }}
                    </div>
                    <Space wrap :size="4">
                      <Tag>{{ item.guard.guardCode }}</Tag>
                      <Tag>{{ item.guard.ownerService || item.resource.ownerService }}</Tag>
                    </Space>
                  </div>
                </div>
              </div>
              <Empty v-else description="暂无资源业务规则" />
            </div>
            <Empty v-else description="保存角色后查看业务规则" />
          </TabPane>

          <TabPane v-if="canQueryAuthz" key="preview" :disabled="!editingRole" tab="决策预览">
            <div v-if="editingRole" class="authorization-tab-body">
              <div class="authorization-inline-form">
                <Input
                  v-model:value="previewForm.userId"
                  class="auth-user-input"
                  allow-clear
                  placeholder="用户 ID"
                />
                <Select
                  v-model:value="previewForm.resourceCode"
                  class="auth-resource-select"
                  :options="resourceOptions"
                  placeholder="业务资源"
                  show-search
                  @change="(value) => changePreviewResource(String(value))"
                />
                <Select
                  v-model:value="previewForm.actionCode"
                  class="auth-action-select"
                  :options="previewActionOptions"
                  placeholder="动作"
                />
                <Input
                  v-model:value="previewForm.businessObjectId"
                  class="auth-user-input"
                  allow-clear
                  placeholder="业务对象 ID"
                />
                <Button
                  :loading="previewLoading"
                  type="primary"
                  @click="runDecisionPreview"
                >
                  预览
                </Button>
              </div>

              <div
                v-if="previewResult"
                class="decision-preview"
                :class="{ denied: !previewResult.allowed }"
              >
                <div class="decision-preview-header">
                  <Tag :color="previewResult.allowed ? 'success' : 'error'">
                    {{ previewResult.allowed ? '允许' : '拒绝' }}
                  </Tag>
                  <span>{{ previewResult.resourceCode }} / {{ previewResult.actionCode }}</span>
                </div>
                <div class="decision-preview-grid">
                  <div>
                    <span>数据范围</span>
                    <strong>
                      {{
                        previewResult.dataScope?.scopeTypes
                          ?.map((item) => dataScopeLabel(item))
                          .join('、') || '-'
                      }}
                    </strong>
                  </div>
                  <div>
                    <span>匹配授权</span>
                    <strong>{{ previewResult.matchedGrantId || '-' }}</strong>
                  </div>
                </div>
                <div v-if="previewResult.reasons?.length" class="decision-reasons">
                  <div
                    v-for="reason in previewResult.reasons"
                    :key="`${reason.code}:${reason.evidenceId}`"
                  >
                    <Tag>{{ reason.source || 'AUTHZ' }}</Tag>
                    <span>{{ reason.message || reason.code }}</span>
                  </div>
                </div>
                <div v-if="previewResult.fieldRules?.length" class="decision-reasons">
                  <div
                    v-for="field in previewResult.fieldRules"
                    :key="field.fieldKey"
                  >
                    <Tag>{{ field.fieldKey }}</Tag>
                    <span>
                      {{ fieldReadModeLabel(field.readMode) }} /
                      {{ fieldWriteModeLabel(field.writeMode) }}
                    </span>
                  </div>
                </div>
                <div
                  v-if="previewResult.guardRequirements?.length"
                  class="decision-reasons"
                >
                  <div
                    v-for="guard in previewResult.guardRequirements"
                    :key="guard.guardCode"
                  >
                    <Tag>{{ guard.guardCode }}</Tag>
                    <span>{{ guard.description || guard.ownerService }}</span>
                  </div>
                </div>
              </div>
            </div>
            <Empty v-else description="保存角色后进行决策预览" />
          </TabPane>
        </Tabs>
      </Form>

      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button
            :disabled="!canSaveRole"
            :loading="saving"
            type="primary"
            @click="submitForm"
          >
            保存
          </Button>
        </Space>
      </template>
    </Drawer>

    <Drawer v-model:open="detailOpen" title="角色详情" width="460">
      <Descriptions v-if="detailRole" bordered :column="1" size="small">
        <DescriptionsItem label="角色 ID">{{ detailRole.id }}</DescriptionsItem>
        <DescriptionsItem label="角色编码">{{ detailRole.roleCode }}</DescriptionsItem>
        <DescriptionsItem label="角色名称">{{ detailRole.roleName }}</DescriptionsItem>
        <DescriptionsItem label="状态">
          <Tag :color="statusValue(detailRole) === 1 ? 'success' : 'error'">
            {{ statusValue(detailRole) === 1 ? '启用' : '禁用' }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="描述">{{ detailRole.description || '-' }}</DescriptionsItem>
        <DescriptionsItem label="创建时间">{{ formatDate(detailRole.createdAt) }}</DescriptionsItem>
        <DescriptionsItem label="可见菜单数量">
          {{ detailRole.menuIds?.length ?? 0 }}
        </DescriptionsItem>
      </Descriptions>
    </Drawer>
  </Page>
</template>

<style scoped>
.role-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 100%;
}

.role-page.is-block-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  height: 100dvh;
  min-height: 0;
  padding: 8px;
  overflow: hidden;
}

.role-page.is-block-fullscreen .role-workbench,
.role-page.is-block-fullscreen .list-panel {
  flex: 1;
}

.query-panel,
.role-tree-panel,
.list-panel {
  background: #fff;
}

.query-panel {
  padding: 8px;
}

.query-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 12px;
  align-items: center;
}

.query-grid :deep(.ant-form-item) {
  margin-bottom: 0;
}

.role-workbench {
  display: flex;
  flex: 1;
  gap: var(--erp-panel-gap);
  min-height: 0;
}

.role-tree-panel {
  display: flex;
  flex: 0 0 var(--erp-master-panel-width);
  flex-direction: column;
  min-height: 0;
  padding: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.role-tree-header {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 38px;
  padding-bottom: 6px;
  border-bottom: 1px solid #edf0f5;
}

.role-tree-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  color: #111827;
}

.role-tree-header span {
  font-size: 12px;
  line-height: 18px;
  color: #6b7280;
}

.role-tree-panel :deep(.ant-tree) {
  flex: 1;
  min-height: 0;
  margin-top: 6px;
  overflow: auto;
  font-size: 13px;
  background: transparent;
}

.role-tree-panel :deep(.ant-tree-treenode) {
  align-items: center;
  width: 100%;
  padding-bottom: 2px;
}

.role-tree-panel :deep(.ant-tree-node-content-wrapper) {
  min-height: 26px;
  line-height: 26px;
  border-radius: 4px;
}

.role-tree-panel :deep(.ant-tree-node-selected) {
  font-weight: 600;
}

.role-tree-hint {
  flex: 0 0 auto;
  padding-top: 6px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
  color: #6b7280;
  border-top: 1px solid #edf0f5;
}

.query-grid :deep(.ant-form-item-label) {
  width: 112px;
  padding-right: 8px;
  font-weight: 600;
}

.query-grid :deep(.ant-form-item-control) {
  min-width: 0;
}

.query-actions {
  display: flex;
  grid-column: -2 / -1;
  gap: 8px;
  justify-content: flex-end;
}

.list-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 0;
  min-height: 0;
  padding: 8px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.list-title {
  display: flex;
  gap: 8px;
  align-items: center;
}

.list-header h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.column-setting-trigger.is-active {
  color: #3164f4;
  border-color: #3164f4;
}

.table-frame {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.table-frame :deep(.ant-table-wrapper),
.table-frame :deep(.ant-spin-nested-loading),
.table-frame :deep(.ant-spin-container) {
  height: 100%;
}

.table-frame :deep(.ant-table) {
  height: 100%;
  border-radius: 4px;
}

.table-frame :deep(.ant-table-container) {
  border-inline-start: 0 !important;
}

.table-frame :deep(.ant-table-thead > tr > th) {
  font-weight: 600;
  color: #2b3340;
  background: #f5f5f5;
}

.table-frame :deep(.ant-table-cell) {
  overflow-wrap: break-word;
}

.table-frame :deep(.ant-empty) {
  margin: 64px 0;
}

.role-name-cell {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
  padding: 6px 2px 0;
}

.table-total {
  font-size: 13px;
  color: #111827;
}

.role-form :deep(.ant-form-item-label) {
  width: 104px;
  font-weight: 600;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  column-gap: 12px;
}

.form-full,
.permission-item {
  grid-column: 1 / -1;
}

.permission-panel {
  width: 100%;
  max-height: 360px;
  padding: 8px 12px;
  overflow: auto;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.authorization-tabs {
  margin-top: 4px;
}

.authorization-tab-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.authorization-toolbar,
.authorization-inline-form,
.authorization-list-item,
.decision-preview-header {
  display: flex;
  gap: 8px;
  align-items: center;
}

.authorization-toolbar,
.authorization-list-item,
.decision-preview-header {
  justify-content: space-between;
}

.authorization-toolbar {
  min-height: 28px;
  font-weight: 600;
  color: #1f2937;
}

.authorization-inline-form {
  flex-wrap: wrap;
}

.auth-resource-select {
  width: 260px;
}

.auth-org-select {
  width: 280px;
}

.auth-action-select,
.auth-field-select,
.auth-mode-select,
.auth-user-input {
  width: 160px;
}

.authorization-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 340px;
  overflow: auto;
}

.authorization-list-item {
  min-height: 48px;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.authorization-list-item p {
  margin: 2px 0 6px;
  font-size: 12px;
  color: #64748b;
}

.authorization-item-title,
.authorization-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.authorization-section-title {
  margin-top: 2px;
}

.decision-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
  background: #f6ffed;
}

.decision-preview.denied {
  border-color: #ffccc7;
  background: #fff2f0;
}

.decision-preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.decision-preview-grid div,
.decision-reasons div {
  min-width: 0;
}

.decision-preview-grid span {
  display: block;
  margin-bottom: 2px;
  font-size: 12px;
  color: #64748b;
}

.decision-preview-grid strong {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.decision-reasons {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.decision-reasons div {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 12px;
  color: #374151;
}

:global(.role-column-popover .ant-popover-inner) {
  padding: 0;
  border-radius: 4px;
}

.column-settings {
  width: 232px;
  color: #1f2937;
}

.column-check-all {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  font-weight: 600;
  color: #3164f4;
}

.column-setting-list {
  padding: 2px 0 6px;
}

.column-setting-item {
  display: grid;
  grid-template-columns: 18px 18px 1fr;
  column-gap: 4px;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  font-size: 14px;
}

.column-setting-item:hover {
  background: #f6f8ff;
}

.drag-icon {
  color: #6b7280;
}

.column-setting-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-setting-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 38px;
  padding: 0 10px;
  border-top: 1px solid #edf0f5;
}

@media (max-width: 1100px) {
  .query-grid {
    grid-template-columns: repeat(2, minmax(240px, 1fr));
    column-gap: 24px;
  }

  .role-workbench {
    flex-direction: column;
  }

  .role-tree-panel {
    flex: 0 0 auto;
    width: 100%;
    max-height: 220px;
  }

  .list-panel {
    width: 100%;
  }
}

@media (max-width: 760px) {
  .query-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .auth-action-select,
  .auth-field-select,
  .auth-mode-select,
  .auth-org-select,
  .auth-resource-select,
  .auth-user-input {
    width: 100%;
  }

  .table-footer {
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }
}
</style>
