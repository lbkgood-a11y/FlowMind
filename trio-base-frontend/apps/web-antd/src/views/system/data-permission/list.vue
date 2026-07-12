<script setup lang="ts">
import type {
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
  FormItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  createDataPolicy,
  deleteDataPolicy,
  getDataPolicies,
  getOrgDimensions,
  getOrgTree,
  getRoleList,
  updateDataPolicy,
} from '#/api';

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
const loading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const editingPolicy = ref<SystemDataPolicyApi.DataPolicy>();

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

const resourceOptions = [
  { label: '用户 USER', value: 'USER' },
  { label: '组织 ORG_UNIT', value: 'ORG_UNIT' },
  { label: '合同 CONTRACT', value: 'CONTRACT' },
  { label: '费用 EXPENSE', value: 'EXPENSE' },
  { label: '订单 ORDER', value: 'ORDER' },
];

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

const columns = computed<TableProps['columns']>(() => [
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
    return;
  }
  roles.value = await getRoleList();
  selectedRoleId.value = selectedRoleId.value ?? roles.value[0]?.id;
}

async function loadDimensions() {
  if (!canQueryOrg.value) {
    dimensions.value = [];
    return;
  }
  dimensions.value = await getOrgDimensions();
}

async function loadPolicies() {
  if (!canQuery.value || !selectedRoleId.value) {
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

watch(selectedRoleId, () => {
  loadPolicies();
});

onMounted(async () => {
  await Promise.all([loadRoles(), loadDimensions()]);
  await loadPolicies();
});
</script>

<template>
  <Page auto-content-height>
    <div class="data-permission-page">
      <section class="toolbar">
        <Space wrap>
          <Select
            v-model:value="selectedRoleId"
            class="role-select"
            :disabled="!canQueryRoles"
            :options="roleOptions"
            placeholder="请选择角色"
          />
          <Button v-if="canCreate" type="primary" :disabled="!selectedRoleId" @click="openCreate">
            <Plus class="size-4" />
            新增策略
          </Button>
          <Tooltip v-if="canQuery" title="刷新">
            <Button shape="circle" @click="loadPolicies">
              <IconifyIcon icon="lucide:refresh-cw" class="size-4" />
            </Button>
          </Tooltip>
        </Space>
      </section>

      <section class="table-shell">
        <Table
          row-key="id"
          :columns="columns"
          :data-source="policies"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1280 }"
          :sticky="{ offsetScroll: 0 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'effect'">
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
      </section>
    </div>

    <Modal
      v-model:open="formOpen"
      :confirm-loading="saving"
      :title="editingPolicy ? '修改数据权限策略' : '新增数据权限策略'"
      ok-text="保存"
      width="860px"
      @ok="submitForm"
    >
      <div class="form-grid">
        <FormItem label="角色" required>
          <Select v-model:value="formModel.roleId" :options="roleOptions" />
        </FormItem>
        <FormItem label="资源" required>
          <Select v-model:value="formModel.resourceCode" :options="resourceOptions" />
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
    </Modal>
  </Page>
</template>

<style scoped>
.data-permission-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 12px;
}

.role-select {
  min-width: 260px;
}

.table-shell {
  overflow: hidden;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}

.form-wide {
  grid-column: 1 / -1;
}

.scope-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0 12px;
}

.scope-row {
  display: grid;
  grid-template-columns: 150px 170px minmax(220px, 1fr) 72px;
  gap: 8px;
  margin-bottom: 8px;
}

@media (max-width: 840px) {
  .form-grid,
  .scope-row {
    grid-template-columns: 1fr;
  }
}
</style>
