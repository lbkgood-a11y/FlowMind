<script setup lang="ts">
import type {
  LowcodeApi,
  SystemDataPolicyApi,
  SystemOrgApi,
  SystemRoleApi,
} from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  Button,
  Drawer,
  FormItem,
  Input,
  message,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Tree,
} from 'ant-design-vue';

import {
  createDataPolicy,
  deleteDataPolicy,
  getDataPolicies,
  getFormDataResources,
  getOrgDimensions,
  getOrgTree,
  getRoleList,
  updateDataPolicy,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';

const Textarea = Input.TextArea;

const DATA_POLICY_PERMISSIONS = {
  create: '/api/v1/data-policies:POST',
  delete: '/api/v1/data-policies/*:DELETE',
  query: '/api/v1/data-policies:GET',
  update: '/api/v1/data-policies/*:PUT',
} as const;

const ROLE_PERMISSIONS = {
  query: '/api/v1/roles:GET',
} as const;

const ORG_PERMISSIONS = {
  query: '/api/v1/org/units:GET',
} as const;

type DimensionForm = {
  dimensionCode: string;
  orgUnitIds: string[];
  scopeType: string;
};

type PolicyFormModel = {
  actionCode: string;
  combineMode: 'AND' | 'OR';
  description?: string;
  dimensions: DimensionForm[];
  effect: 'ALLOW' | 'DENY';
  resourceCode: string;
  roleId: string;
  status: 0 | 1;
};

type RoleTreeNode = {
  children?: RoleTreeNode[];
  key: string;
  selectable?: boolean;
  title: string;
};

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([DATA_POLICY_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([DATA_POLICY_PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([DATA_POLICY_PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([DATA_POLICY_PERMISSIONS.delete]));
const canQueryRoles = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.query]));
const canQueryOrg = computed(() => hasAccessByCodes([ORG_PERMISSIONS.query]));

const roles = ref<SystemRoleApi.SystemRole[]>([]);
const dimensions = ref<SystemOrgApi.OrgDimension[]>([]);
const policies = ref<SystemDataPolicyApi.DataPolicy[]>([]);
const orgOptionsMap = ref<Record<string, { label: string; value: string }[]>>({});
const selectedRoleId = ref<string>();
const selectedRoleTreeKey = ref('all');
const expandedRoleTreeKeys = ref<Array<number | string>>([
  'enabled-group',
  'disabled-group',
]);
const loading = ref(false);
const saving = ref(false);
const resourcesLoading = ref(false);
const formOpen = ref(false);
const editingPolicy = ref<SystemDataPolicyApi.DataPolicy>();
const formDataResources = ref<LowcodeApi.FormDataResource[]>([]);

const formModel = reactive<PolicyFormModel>({
  actionCode: 'QUERY',
  combineMode: 'AND',
  description: '',
  dimensions: [{ dimensionCode: 'ADMIN', orgUnitIds: [], scopeType: 'OWN_ORG_AND_CHILDREN' }],
  effect: 'ALLOW',
  resourceCode: 'USER',
  roleId: '',
  status: 1,
});

const builtInResourceOptions = [
  { label: '用户 USER', value: 'USER' },
  { label: '组织 ORG_UNIT', value: 'ORG_UNIT' },
];

const resourceOptions = computed(() => [
  {
    label: '平台内置资源',
    options: builtInResourceOptions,
  },
  {
    label: '已发布低代码表单',
    options: formDataResources.value.map((item) => ({
      label: `${item.resourceName}（${item.formKey}）`,
      value: item.resourceCode,
    })),
  },
]);

const resourceLabelMap = computed(() =>
  new Map([
    ...builtInResourceOptions.map((item) => [item.value, item.label] as const),
    ...formDataResources.value.map(
      (item) =>
        [item.resourceCode, `${item.resourceName}（${item.formKey}）`] as const,
    ),
  ]),
);

const actionOptions = [
  { label: '查询', value: 'QUERY' },
  { label: '创建', value: 'CREATE' },
  { label: '修改', value: 'UPDATE' },
  { label: '审批', value: 'APPROVE' },
  { label: '导出', value: 'EXPORT' },
];

const scopeOptions = [
  { label: '本人', value: 'SELF' },
  { label: '本组织', value: 'OWN_ORG' },
  { label: '本组织及下级', value: 'OWN_ORG_AND_CHILDREN' },
  { label: '指定组织', value: 'ASSIGNED_ORGS' },
  { label: '全部', value: 'ALL' },
];

const dimensionOptions = computed(() =>
  dimensions.value.map((item) => ({
    label: item.dimensionName,
    value: item.dimensionCode,
  })),
);

const roleOptions = computed(() =>
  roles.value.map((item) => ({
    label: `${item.roleName} (${item.roleCode})`,
    value: item.id,
  })),
);

const roleMap = computed(() => new Map(roles.value.map((item) => [item.id, item])));
const selectedRole = computed(() =>
  selectedRoleId.value ? roleMap.value.get(selectedRoleId.value) : undefined,
);
const roleTreeData = computed<RoleTreeNode[]>(() => [
  { key: 'all', title: '全部策略' },
  {
    children: roles.value
      .filter((item) => (item.status ?? 1) === 1)
      .map((item) => ({
        key: item.id,
        title: `${item.roleName} (${item.roleCode})`,
      })),
    key: 'enabled-group',
    selectable: false,
    title: '启用角色',
  },
  {
    children: roles.value
      .filter((item) => item.status === 0)
      .map((item) => ({
        key: item.id,
        title: `${item.roleName} (${item.roleCode})`,
      })),
    key: 'disabled-group',
    selectable: false,
    title: '停用角色',
  },
]);
const selectedRoleTreeKeys = computed(() => [selectedRoleTreeKey.value]);
const policyContextText = computed(() =>
  selectedRole.value
    ? `${selectedRole.value.roleName} 的策略`
    : '全部数据权限策略',
);

const columns = computed<TableProps['columns']>(() => [
  { dataIndex: 'roleId', fixed: 'left', key: 'role', title: '角色', width: 180 },
  { dataIndex: 'resourceCode', fixed: 'left', key: 'resourceCode', title: '资源', width: 140 },
  { dataIndex: 'actionCode', key: 'actionCode', title: '动作', width: 110 },
  { dataIndex: 'effect', key: 'effect', title: '效果', width: 100 },
  { dataIndex: 'combineMode', key: 'combineMode', title: '维度组合', width: 110 },
  { key: 'dimensions', title: '组织范围', width: 320 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 100 },
  { dataIndex: 'description', key: 'description', title: '描述', width: 260 },
  { fixed: 'right', key: 'action', title: '操作', width: 150 },
]);

async function loadRoles() {
  if (!canQueryRoles.value) {
    roles.value = [];
    selectedRoleId.value = undefined;
    selectedRoleTreeKey.value = 'all';
    return;
  }
  roles.value = await getRoleList();
  if (selectedRoleId.value && !roles.value.some((item) => item.id === selectedRoleId.value)) {
    selectedRoleId.value = undefined;
    selectedRoleTreeKey.value = 'all';
  }
}

async function loadDimensions() {
  if (!canQueryOrg.value) {
    dimensions.value = [];
    return;
  }
  dimensions.value = await getOrgDimensions();
}

async function loadPolicies() {
  if (!canQuery.value) {
    policies.value = [];
    return;
  }
  loading.value = true;
  try {
    policies.value = await getDataPolicies(selectedRoleId.value);
  } finally {
    loading.value = false;
  }
}

async function loadDataResources() {
  resourcesLoading.value = true;
  try {
    formDataResources.value = await getFormDataResources();
  } catch {
    formDataResources.value = [];
    message.warning('低代码表单资源加载失败，当前仅展示平台内置资源');
  } finally {
    resourcesLoading.value = false;
  }
}

function selectRoleNode(keys: Array<number | string>) {
  const key = keys[0] ? String(keys[0]) : 'all';
  selectedRoleTreeKey.value = key;
  selectedRoleId.value = key === 'all' ? undefined : key;
}

async function refreshWorkbench() {
  await loadRoles();
  await loadPolicies();
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
  if (!canQueryOrg.value || orgOptionsMap.value[dimensionCode]) {
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

function resetForm() {
  editingPolicy.value = undefined;
  formModel.actionCode = 'QUERY';
  formModel.combineMode = 'AND';
  formModel.description = '';
  formModel.dimensions = [
    {
      dimensionCode: dimensions.value[0]?.dimensionCode ?? 'ADMIN',
      orgUnitIds: [],
      scopeType: 'OWN_ORG_AND_CHILDREN',
    },
  ];
  formModel.effect = 'ALLOW';
  formModel.resourceCode = 'USER';
  formModel.roleId = selectedRoleId.value ?? '';
  formModel.status = 1;
}

async function openCreate() {
  resetForm();
  await ensureOrgOptions(formModel.dimensions[0]?.dimensionCode ?? 'ADMIN');
  formOpen.value = true;
}

async function openEdit(record: SystemDataPolicyApi.DataPolicy) {
  editingPolicy.value = record;
  formModel.actionCode = record.actionCode;
  formModel.combineMode = record.combineMode;
  formModel.description = record.description ?? '';
  formModel.dimensions = record.dimensions.map((item) => ({
    dimensionCode: item.dimensionCode,
    orgUnitIds: item.orgUnitIds ?? [],
    scopeType: item.scopeType,
  }));
  formModel.effect = record.effect;
  formModel.resourceCode = record.resourceCode;
  formModel.roleId = record.roleId;
  formModel.status = record.status ?? 1;
  await Promise.all(formModel.dimensions.map((item) => ensureOrgOptions(item.dimensionCode)));
  formOpen.value = true;
}

function addDimensionScope() {
  const dimensionCode = dimensions.value[0]?.dimensionCode ?? 'ADMIN';
  formModel.dimensions.push({
    dimensionCode,
    orgUnitIds: [],
    scopeType: 'OWN_ORG_AND_CHILDREN',
  });
  ensureOrgOptions(dimensionCode);
}

function removeDimensionScope(index: number) {
  formModel.dimensions.splice(index, 1);
}

async function changeDimensionScope(index: number, dimensionCode: string) {
  const scope = formModel.dimensions[index];
  if (!scope) {
    return;
  }
  scope.dimensionCode = dimensionCode;
  scope.orgUnitIds = [];
  await ensureOrgOptions(dimensionCode);
}

function validateForm() {
  if (!formModel.roleId) {
    message.warning('请选择角色');
    return false;
  }
  if (!formModel.resourceCode.trim() || !formModel.actionCode.trim()) {
    message.warning('请选择资源和动作');
    return false;
  }
  if (formModel.dimensions.length === 0) {
    message.warning('请至少配置一个组织维度范围');
    return false;
  }
  const invalidAssigned = formModel.dimensions.some(
    (item) => item.scopeType === 'ASSIGNED_ORGS' && item.orgUnitIds.length === 0,
  );
  if (invalidAssigned) {
    message.warning('指定组织范围必须选择组织');
    return false;
  }
  return true;
}

async function submitForm() {
  if (editingPolicy.value ? !canUpdate.value : !canCreate.value) {
    message.warning('当前账号没有保存数据权限的权限');
    return;
  }
  if (!validateForm()) {
    return;
  }
  saving.value = true;
  try {
    const payload: SystemDataPolicyApi.SaveDataPolicyParams = {
      actionCode: formModel.actionCode,
      combineMode: formModel.combineMode,
      description: formModel.description?.trim() || undefined,
      dimensions: formModel.dimensions.map((item, index) => ({
        dimensionCode: item.dimensionCode,
        orgUnitIds: item.scopeType === 'ASSIGNED_ORGS' ? item.orgUnitIds : [],
        scopeType: item.scopeType,
        sortOrder: (index + 1) * 10,
      })),
      effect: formModel.effect,
      resourceCode: formModel.resourceCode,
      roleId: formModel.roleId,
      status: formModel.status,
    };
    if (editingPolicy.value) {
      await updateDataPolicy(editingPolicy.value.id, payload);
      message.success('数据权限策略已更新');
    } else {
      await createDataPolicy(payload);
      message.success('数据权限策略已创建');
    }
    formOpen.value = false;
    await loadPolicies();
  } finally {
    saving.value = false;
  }
}

async function removePolicy(record: SystemDataPolicyApi.DataPolicy) {
  await deleteDataPolicy(record.id);
  message.success('数据权限策略已删除');
  await loadPolicies();
}

function asPolicy(record: Record<string, any>) {
  return record as SystemDataPolicyApi.DataPolicy;
}

function scopeLabel(value: string) {
  return scopeOptions.find((item) => item.value === value)?.label ?? value;
}

function roleLabel(roleId: string) {
  const role = roleMap.value.get(roleId);
  return role ? `${role.roleName} (${role.roleCode})` : roleId;
}

function resourceLabel(resourceCode: string) {
  return resourceLabelMap.value.get(resourceCode) ?? resourceCode;
}

watch(selectedRoleId, () => {
  loadPolicies();
});

onMounted(async () => {
  await Promise.all([loadRoles(), loadDimensions(), loadDataResources()]);
  await loadPolicies();
});
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page data-permission-page">
      <section class="policy-workbench">
        <aside class="data-panel policy-role-panel">
          <div class="role-tree-header">
            <div>
              <h3>角色树</h3>
              <span>{{ selectedRole ? selectedRole.roleCode : 'ALL' }}</span>
            </div>
            <Tooltip title="全部策略">
              <Button
                :disabled="selectedRoleTreeKey === 'all'"
                shape="circle"
                size="small"
                @click="selectRoleNode(['all'])"
              >
                <IconifyIcon :icon="ERP_TOOLBAR_ICONS.reset" class="size-3.5" />
              </Button>
            </Tooltip>
          </div>

          <div class="role-tree-toolbar">
            <Tooltip v-if="canQuery" title="刷新">
              <Button block @click="refreshWorkbench">
                <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
                刷新角色
              </Button>
            </Tooltip>
          </div>

          <Tree
            v-model:expanded-keys="expandedRoleTreeKeys"
            :selected-keys="selectedRoleTreeKeys"
            :tree-data="roleTreeData"
            block-node
            @select="selectRoleNode"
          />

          <div class="role-tree-hint">
            {{ policyContextText }}
          </div>
        </aside>

        <section class="data-panel policy-table-panel">
          <div class="list-header">
            <div class="list-title">
              <h2>数据权限策略</h2>
              <Tag :color="selectedRole ? 'blue' : 'default'">{{ policyContextText }}</Tag>
            </div>
            <Space :size="8">
              <Button v-if="canCreate" type="primary" @click="openCreate">
                <Plus class="size-4" />
                新增策略
              </Button>
              <Tooltip v-if="canQuery" title="刷新">
                <Button shape="circle" @click="loadPolicies">
                  <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
                </Button>
              </Tooltip>
            </Space>
          </div>

          <div class="table-shell">
            <Table
              row-key="id"
              :columns="columns"
              :data-source="policies"
              :loading="loading"
              :pagination="false"
              :scroll="{ x: 1460 }"
              size="small"
              :sticky="{ offsetScroll: 0 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'role'">
                  {{ roleLabel(record.roleId) }}
                </template>
                <template v-else-if="column.key === 'resourceCode'">
                  <div class="resource-cell">
                    <span>{{ resourceLabel(record.resourceCode) }}</span>
                    <code>{{ record.resourceCode }}</code>
                  </div>
                </template>
                <template v-else-if="column.key === 'effect'">
                  <Tag :color="record.effect === 'DENY' ? 'red' : 'green'">
                    {{ record.effect }}
                  </Tag>
                </template>
                <template v-else-if="column.key === 'dimensions'">
                  <Space wrap>
                    <Tag
                      v-for="item in asPolicy(record).dimensions"
                      :key="`${asPolicy(record).id}-${item.dimensionCode}`"
                    >
                      {{ item.dimensionCode }} / {{ scopeLabel(item.scopeType) }}
                    </Tag>
                  </Space>
                </template>
                <template v-else-if="column.key === 'status'">
                  <Tag :color="record.status === 0 ? 'default' : 'green'">
                    {{ record.status === 0 ? '禁用' : '启用' }}
                  </Tag>
                </template>
                <template v-else-if="column.key === 'description'">
                  {{ record.description || '-' }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <Space :size="4">
                    <Button v-if="canUpdate" type="link" size="small" @click="openEdit(asPolicy(record))">
                      修改
                    </Button>
                    <Popconfirm
                      v-if="canDelete"
                      title="确认删除该策略？"
                      @confirm="removePolicy(asPolicy(record))"
                    >
                      <Button danger type="link" size="small">删除</Button>
                    </Popconfirm>
                  </Space>
                </template>
              </template>
            </Table>
          </div>
        </section>
      </section>
    </div>

    <Drawer
      v-model:open="formOpen"
      :title="editingPolicy ? '修改数据权限策略' : '新增数据权限策略'"
      placement="right"
      width="860"
    >
      <div class="form-grid">
        <FormItem label="角色" required>
          <Select v-model:value="formModel.roleId" :options="roleOptions" />
        </FormItem>
        <FormItem label="资源" required>
          <Select
            v-model:value="formModel.resourceCode"
            show-search
            :loading="resourcesLoading"
            :options="resourceOptions"
            option-filter-prop="label"
            placeholder="选择平台资源或已发布表单"
          />
          <div class="field-hint">
            低代码表单发布后会自动出现在此处，策略通过资源编码绑定真实表单。
          </div>
        </FormItem>
        <FormItem label="动作" required>
          <Select v-model:value="formModel.actionCode" :options="actionOptions" />
        </FormItem>
        <FormItem label="效果">
          <Select
            v-model:value="formModel.effect"
            :options="[
              { label: '允许 ALLOW', value: 'ALLOW' },
              { label: '拒绝 DENY', value: 'DENY' },
            ]"
          />
        </FormItem>
        <FormItem label="维度组合">
          <Select
            v-model:value="formModel.combineMode"
            :options="[
              { label: '交集 AND', value: 'AND' },
              { label: '并集 OR', value: 'OR' },
            ]"
          />
        </FormItem>
        <FormItem label="状态">
          <Switch
            v-model:checked="formModel.status"
            :checked-value="1"
            :un-checked-value="0"
            checked-children="启用"
            un-checked-children="禁用"
          />
        </FormItem>
        <FormItem class="form-wide" label="描述">
          <Textarea v-model:value="formModel.description" :rows="2" placeholder="请输入描述" />
        </FormItem>
      </div>

      <div class="scope-header">
        <strong>组织维度范围</strong>
        <Button size="small" @click="addDimensionScope">新增维度</Button>
      </div>
      <div
        v-for="(item, index) in formModel.dimensions"
        :key="index"
        class="scope-row"
      >
        <Select
          :value="item.dimensionCode"
          :options="dimensionOptions"
          @change="(value) => changeDimensionScope(index, String(value))"
        />
        <Select v-model:value="item.scopeType" :options="scopeOptions" />
        <Select
          v-model:value="item.orgUnitIds"
          mode="multiple"
          :disabled="item.scopeType !== 'ASSIGNED_ORGS'"
          :options="orgOptionsMap[item.dimensionCode] ?? []"
          placeholder="指定组织时选择"
        />
        <Button danger :disabled="formModel.dimensions.length === 1" @click="removeDimensionScope(index)">
          删除
        </Button>
      </div>

      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitForm">保存</Button>
        </Space>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.data-permission-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 100%;
}

.policy-workbench {
  display: flex;
  flex: 1;
  gap: var(--erp-panel-gap);
  min-height: 0;
}

.policy-role-panel {
  display: flex;
  flex: 0 0 var(--erp-master-panel-width);
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.role-tree-header {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
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

.role-tree-toolbar {
  padding: 6px 0;
}

.policy-role-panel :deep(.ant-tree) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  font-size: 13px;
  background: transparent;
}

.policy-role-panel :deep(.ant-tree-treenode) {
  align-items: center;
  width: 100%;
  padding-bottom: 2px;
}

.policy-role-panel :deep(.ant-tree-node-content-wrapper) {
  min-height: 26px;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 26px;
  white-space: nowrap;
  border-radius: 4px;
}

.policy-role-panel :deep(.ant-tree-node-selected) {
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

.policy-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 0;
  min-height: 0;
}

.list-header {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.list-title {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.list-title h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.table-shell {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.resource-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.resource-cell code {
  font-size: 11px;
  color: #94a3b8;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.form-wide {
  grid-column: 1 / -1;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #94a3b8;
}

.scope-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6px 0 8px;
}

.scope-row {
  display: grid;
  grid-template-columns: 150px 170px minmax(220px, 1fr) 72px;
  gap: 8px;
  margin-bottom: 8px;
}

@media (max-width: 840px) {
  .policy-workbench {
    flex-direction: column;
  }

  .policy-role-panel {
    flex: 0 0 auto;
    max-height: 260px;
  }

  .policy-table-panel {
    width: 100%;
  }

  .form-grid,
  .scope-row {
    grid-template-columns: 1fr;
  }
}
</style>
