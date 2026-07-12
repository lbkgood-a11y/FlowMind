<script setup lang="ts">
import type { SystemOrgApi } from '#/api';
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
  InputNumber,
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
  assignUserOrgUnits,
  createOrgUnit,
  deleteOrgRelation,
  deleteOrgUnit,
  getOrgDimensions,
  getOrgTree,
  getOrgUnitUsers,
  getUserOrgAssignments,
  saveOrgRelation,
  updateOrgUnit,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';

const Textarea = Input.TextArea;

const ORG_PERMISSIONS = {
  create: '/api/v1/org/units:POST',
  delete: '/api/v1/org/units/*:DELETE',
  query: '/api/v1/org/units:GET',
  update: '/api/v1/org/units/*:PUT',
} as const;

type OrgFormModel = {
  description?: string;
  enabled: boolean;
  parentUnitId?: string;
  sortOrder: number;
  unitCode: string;
  unitName: string;
  unitType: string;
};

type OrgTreeDataNode = {
  children?: OrgTreeDataNode[];
  key: string;
  title: string;
};

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([ORG_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([ORG_PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([ORG_PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([ORG_PERMISSIONS.delete]));

const dimensions = ref<SystemOrgApi.OrgDimension[]>([]);
const activeDimension = ref('ADMIN');
const treeRows = ref<SystemOrgApi.OrgTreeNode[]>([]);
const orgUsers = ref<SystemOrgApi.OrgUnitUser[]>([]);
const loading = ref(false);
const usersLoading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const assignmentOpen = ref(false);
const editingNode = ref<SystemOrgApi.OrgTreeNode>();
const userIdForAssignment = ref('');
const assignmentOrgUnitIds = ref<string[]>([]);
const primaryOrgUnitId = ref<string>();
const assignmentPosition = ref('');
const selectedOrgUnitId = ref<string>();
const expandedOrgTreeKeys = ref<Array<number | string>>([]);

const formModel = reactive<OrgFormModel>({
  description: '',
  enabled: true,
  parentUnitId: undefined,
  sortOrder: 100,
  unitCode: '',
  unitName: '',
  unitType: 'DEPARTMENT',
});

const unitTypeOptions = [
  { label: '公司', value: 'COMPANY' },
  { label: '部门', value: 'DEPARTMENT' },
  { label: '小组', value: 'TEAM' },
  { label: '成本中心', value: 'COST_CENTER' },
  { label: '利润中心', value: 'PROFIT_CENTER' },
  { label: '项目组', value: 'PROJECT' },
  { label: '门店', value: 'STORE' },
];
const unitTypeLabelMap = new Map(
  unitTypeOptions.map((item) => [item.value, item.label]),
);

const columns = computed<TableProps['columns']>(() => [
  { dataIndex: 'unitName', fixed: 'left', key: 'unitName', title: '组织名称', width: 220 },
  { dataIndex: 'unitCode', key: 'unitCode', title: '组织编码', width: 160 },
  { dataIndex: 'unitType', key: 'unitType', title: '实体类型', width: 130 },
  { dataIndex: 'level', key: 'level', title: '层级', width: 90 },
  { dataIndex: 'sortOrder', key: 'sortOrder', title: '排序', width: 90 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 100 },
  { dataIndex: 'description', key: 'description', title: '描述', width: 260 },
  { fixed: 'right', key: 'action', title: '操作', width: 260 },
]);

const userColumns = computed<TableProps['columns']>(() => [
  { dataIndex: 'username', fixed: 'left', key: 'username', title: '用户名', width: 150 },
  { dataIndex: 'userId', key: 'userId', title: '用户ID', width: 180 },
  { dataIndex: 'positionName', key: 'positionName', title: '岗位', width: 150 },
  { dataIndex: 'primary', key: 'primary', title: '主组织', width: 90 },
  { dataIndex: 'leader', key: 'leader', title: '负责人', width: 90 },
  { dataIndex: 'userStatus', key: 'userStatus', title: '用户状态', width: 100 },
  { dataIndex: 'status', key: 'status', title: '归属状态', width: 100 },
  { dataIndex: 'email', key: 'email', title: '邮箱', width: 220 },
  { dataIndex: 'phone', key: 'phone', title: '手机', width: 140 },
]);

const dimensionOptions = computed(() =>
  dimensions.value.map((item) => ({
    label: item.dimensionName,
    value: item.dimensionCode,
  })),
);

const flatRows = computed(() => flattenTree(treeRows.value));
const selectedOrgNode = computed(() =>
  flatRows.value.find((item) => item.id === selectedOrgUnitId.value),
);
const selectedOrgTreeKeys = computed(() =>
  selectedOrgUnitId.value ? [selectedOrgUnitId.value] : [],
);
const tableRows = computed(() =>
  selectedOrgNode.value
    ? [
        toPlainOrgTableRow(selectedOrgNode.value),
        ...(selectedOrgNode.value.children ?? []).map(toPlainOrgTableRow),
      ]
    : treeRows.value.map(toPlainOrgTableRow),
);
const activeDimensionName = computed(
  () =>
    dimensions.value.find((item) => item.dimensionCode === activeDimension.value)
      ?.dimensionName ?? activeDimension.value,
);
const tableContextText = computed(() =>
  selectedOrgNode.value
    ? `${selectedOrgNode.value.unitName} 及下级组织`
    : '全部根组织',
);
const userContextText = computed(() =>
  selectedOrgNode.value
    ? `${selectedOrgNode.value.unitName} 下的用户`
    : '请先选择组织',
);
const orgTreeData = computed(() => buildOrgTreeData(treeRows.value));
const parentOptions = computed(() =>
  flatRows.value
    .filter((item) => !formOpen.value || item.id !== editingNode.value?.id)
    .map((item) => ({
      label: `${item.unitName} (${item.unitCode})`,
      value: item.id,
    })),
);

function flattenTree(list: SystemOrgApi.OrgTreeNode[]) {
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

function buildTree(list: SystemOrgApi.OrgTreeNode[]) {
  const nodeMap = new Map<string, SystemOrgApi.OrgTreeNode>();
  list.forEach((item) => {
    nodeMap.set(item.id, { ...item, children: [] });
  });
  const roots: SystemOrgApi.OrgTreeNode[] = [];
  list.forEach((item) => {
    const node = nodeMap.get(item.id);
    if (!node) {
      return;
    }
    if (item.parentUnitId && nodeMap.has(item.parentUnitId)) {
      nodeMap.get(item.parentUnitId)?.children?.push(node);
    } else {
      roots.push(node);
    }
  });
  const sortNodes = (nodes: SystemOrgApi.OrgTreeNode[]) => {
    nodes.sort((a, b) => (a.sortOrder ?? 100) - (b.sortOrder ?? 100));
    nodes.forEach((node) => {
      if (node.children?.length) {
        sortNodes(node.children);
      } else {
        delete node.children;
      }
    });
  };
  sortNodes(roots);
  return roots;
}

function buildOrgTreeData(list: SystemOrgApi.OrgTreeNode[]): OrgTreeDataNode[] {
  return list.map((item) => ({
    children: item.children?.length ? buildOrgTreeData(item.children) : undefined,
    key: item.id,
    title: `${item.unitName} (${item.unitCode})`,
  }));
}

function collectOrgTreeKeys(list: SystemOrgApi.OrgTreeNode[]) {
  const keys: string[] = [];
  const walk = (nodes: SystemOrgApi.OrgTreeNode[]) => {
    nodes.forEach((node) => {
      keys.push(node.id);
      if (node.children?.length) {
        walk(node.children);
      }
    });
  };
  walk(list);
  return keys;
}

function toPlainOrgTableRow(node: SystemOrgApi.OrgTreeNode) {
  const { children, ...row } = node;
  return row;
}

async function loadDimensions() {
  if (!canQuery.value) {
    dimensions.value = [];
    return;
  }
  dimensions.value = await getOrgDimensions();
  activeDimension.value =
    dimensions.value.find((item) => item.isDefault === 1)?.dimensionCode ??
    dimensions.value[0]?.dimensionCode ??
    'ADMIN';
}

async function loadTree() {
  if (!canQuery.value) {
    treeRows.value = [];
    selectedOrgUnitId.value = undefined;
    expandedOrgTreeKeys.value = [];
    return;
  }
  loading.value = true;
  try {
    const nextTree = buildTree(await getOrgTree(activeDimension.value));
    treeRows.value = nextTree;
    expandedOrgTreeKeys.value = collectOrgTreeKeys(nextTree);
    if (
      selectedOrgUnitId.value &&
      !flattenTree(nextTree).some((item) => item.id === selectedOrgUnitId.value)
    ) {
      selectedOrgUnitId.value = undefined;
    }
  } finally {
    loading.value = false;
  }
}

async function loadOrgUsers() {
  if (!canQuery.value || !selectedOrgUnitId.value) {
    orgUsers.value = [];
    return;
  }
  usersLoading.value = true;
  try {
    orgUsers.value = await getOrgUnitUsers(selectedOrgUnitId.value, activeDimension.value);
  } finally {
    usersLoading.value = false;
  }
}

async function changeDimension(value: string) {
  activeDimension.value = value;
  selectedOrgUnitId.value = undefined;
  await loadTree();
}

function selectOrgNode(keys: Array<number | string>) {
  selectedOrgUnitId.value = keys[0] ? String(keys[0]) : undefined;
}

function clearOrgSelection() {
  selectedOrgUnitId.value = undefined;
}

function orgRowClassName(record: SystemOrgApi.OrgTreeNode) {
  return record.id === selectedOrgUnitId.value ? 'is-current-org-row' : '';
}

function asOrgNode(record: Record<string, any>) {
  return record as SystemOrgApi.OrgTreeNode;
}

function formatUnitType(unitType?: string) {
  const normalizedUnitType = unitType || 'DEPARTMENT';
  return unitTypeLabelMap.get(normalizedUnitType) ?? normalizedUnitType;
}

function resetForm(parentUnitId?: string) {
  editingNode.value = undefined;
  formModel.description = '';
  formModel.enabled = true;
  formModel.parentUnitId = parentUnitId;
  formModel.sortOrder = 100;
  formModel.unitCode = '';
  formModel.unitName = '';
  formModel.unitType = 'DEPARTMENT';
}

function openCreate(parent?: SystemOrgApi.OrgTreeNode) {
  resetForm(parent?.id);
  formOpen.value = true;
}

function openEdit(record: SystemOrgApi.OrgTreeNode) {
  editingNode.value = record;
  formModel.description = record.description ?? '';
  formModel.enabled = (record.status ?? 1) === 1;
  formModel.parentUnitId = record.parentUnitId;
  formModel.sortOrder = record.sortOrder ?? 100;
  formModel.unitCode = record.unitCode;
  formModel.unitName = record.unitName;
  formModel.unitType = record.unitType ?? 'DEPARTMENT';
  formOpen.value = true;
}

function validateForm() {
  if (!editingNode.value && !formModel.unitCode.trim()) {
    message.warning('请输入组织编码');
    return false;
  }
  if (!formModel.unitName.trim()) {
    message.warning('请输入组织名称');
    return false;
  }
  return true;
}

async function submitForm() {
  if (editingNode.value ? !canUpdate.value : !canCreate.value) {
    message.warning('当前账号没有保存组织的权限');
    return;
  }
  if (!validateForm()) {
    return;
  }
  saving.value = true;
  try {
    if (editingNode.value) {
      await updateOrgUnit(editingNode.value.id, {
        description: formModel.description?.trim() || undefined,
        enabled: formModel.enabled,
        sortOrder: formModel.sortOrder,
        unitName: formModel.unitName.trim(),
        unitType: formModel.unitType,
      });
      await saveOrgRelation(activeDimension.value, editingNode.value.id, {
        enabled: formModel.enabled,
        parentUnitId: formModel.parentUnitId,
        sortOrder: formModel.sortOrder,
      });
      message.success('组织已更新');
    } else {
      await createOrgUnit({
        description: formModel.description?.trim() || undefined,
        dimensionCode: activeDimension.value,
        enabled: formModel.enabled,
        parentId: formModel.parentUnitId,
        sortOrder: formModel.sortOrder,
        unitCode: formModel.unitCode.trim(),
        unitName: formModel.unitName.trim(),
        unitType: formModel.unitType,
      });
      message.success('组织已创建');
    }
    formOpen.value = false;
    await loadTree();
  } finally {
    saving.value = false;
  }
}

async function removeNode(record: SystemOrgApi.OrgTreeNode) {
  if (activeDimension.value === 'ADMIN') {
    await deleteOrgUnit(record.id);
  } else {
    await deleteOrgRelation(activeDimension.value, record.id);
  }
  message.success(activeDimension.value === 'ADMIN' ? '组织已删除' : '组织已移出当前维度');
  await loadTree();
}

async function openAssignment(record: SystemOrgApi.OrgTreeNode) {
  userIdForAssignment.value = '';
  assignmentOrgUnitIds.value = [record.id];
  primaryOrgUnitId.value = record.id;
  assignmentPosition.value = '';
  assignmentOpen.value = true;
}

async function loadUserAssignments() {
  if (!userIdForAssignment.value.trim()) {
    message.warning('请输入用户ID');
    return;
  }
  const assignments = await getUserOrgAssignments(
    userIdForAssignment.value.trim(),
    activeDimension.value,
  );
  assignmentOrgUnitIds.value = assignments.map((item) => item.orgUnitId);
  primaryOrgUnitId.value =
    assignments.find((item) => item.primary)?.orgUnitId ?? assignments[0]?.orgUnitId;
  assignmentPosition.value = assignments[0]?.positionName ?? '';
}

async function submitAssignment() {
  if (!canUpdate.value) {
    message.warning('当前账号没有维护用户组织归属的权限');
    return;
  }
  if (!userIdForAssignment.value.trim()) {
    message.warning('请输入用户ID');
    return;
  }
  if (assignmentOrgUnitIds.value.length === 0) {
    message.warning('请选择组织');
    return;
  }
  await assignUserOrgUnits(userIdForAssignment.value.trim(), {
    assignments: assignmentOrgUnitIds.value.map((orgUnitId) => ({
      orgUnitId,
      positionName: assignmentPosition.value.trim() || undefined,
      primary: orgUnitId === primaryOrgUnitId.value,
      status: 1,
    })),
    dimensionCode: activeDimension.value,
    primaryOrgUnitId: primaryOrgUnitId.value,
  });
  message.success('用户组织归属已更新');
  assignmentOpen.value = false;
  await loadOrgUsers();
}

watch([selectedOrgUnitId, activeDimension], () => {
  loadOrgUsers();
});

onMounted(async () => {
  await loadDimensions();
  await loadTree();
});
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page org-page">
      <section class="org-workbench">
        <aside class="data-panel org-tree-panel">
          <div class="org-tree-header">
            <div>
              <h3>组织树</h3>
              <span>{{ activeDimensionName }}</span>
            </div>
            <Tooltip title="返回全部组织">
              <Button
                :disabled="!selectedOrgUnitId"
                shape="circle"
                size="small"
                @click="clearOrgSelection"
              >
                <IconifyIcon :icon="ERP_TOOLBAR_ICONS.reset" class="size-3.5" />
              </Button>
            </Tooltip>
          </div>

          <div class="org-tree-toolbar">
            <Select
              class="dimension-select"
              :disabled="!canQuery"
              :options="dimensionOptions"
              placeholder="请选择组织维度"
              :value="activeDimension"
              @change="(value) => changeDimension(String(value || activeDimension))"
            />
            <Tooltip v-if="canQuery" title="刷新">
              <Button shape="circle" @click="loadTree">
                <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
              </Button>
            </Tooltip>
          </div>

          <Tree
            v-model:expanded-keys="expandedOrgTreeKeys"
            :selected-keys="selectedOrgTreeKeys"
            :tree-data="orgTreeData"
            block-node
            @select="selectOrgNode"
          />

          <div class="org-tree-hint">
            {{ selectedOrgNode ? selectedOrgNode.unitCode : '选择左侧节点查看下级组织' }}
          </div>
        </aside>

        <section class="org-table-panel">
          <div class="data-panel org-upper-panel">
            <div class="list-header">
              <div class="list-title">
                <h2>组织列表</h2>
                <Tag color="blue">{{ tableContextText }}</Tag>
              </div>
              <Space :size="8">
                <Button
                  v-if="canCreate"
                  type="primary"
                  @click="openCreate(selectedOrgNode)"
                >
                  <Plus class="size-4" />
                  {{ selectedOrgNode ? '新增下级' : '新增组织' }}
                </Button>
                <Button
                  v-if="canUpdate && selectedOrgNode"
                  @click="openEdit(selectedOrgNode)"
                >
                  编辑当前
                </Button>
                <Button
                  v-if="canUpdate && selectedOrgNode"
                  @click="openAssignment(selectedOrgNode)"
                >
                  归属
                </Button>
                <Tooltip v-if="canQuery" title="刷新">
                  <Button shape="circle" @click="loadTree">
                    <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
                  </Button>
                </Tooltip>
              </Space>
            </div>

            <div class="table-frame">
              <Table
                row-key="id"
                :columns="columns"
                :data-source="tableRows"
                :loading="loading"
                :pagination="false"
                :row-class-name="orgRowClassName"
                :scroll="{ x: 1280 }"
                size="small"
                :sticky="{ offsetScroll: 0 }"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'unitType'">
                    <Tag>{{ formatUnitType(record.unitType) }}</Tag>
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
                    <Space :size="4" wrap>
                      <Button
                        v-if="canCreate"
                        type="link"
                        size="small"
                        @click="openCreate(asOrgNode(record))"
                      >
                        新增下级
                      </Button>
                      <Button
                        v-if="canUpdate"
                        type="link"
                        size="small"
                        @click="openEdit(asOrgNode(record))"
                      >
                        修改
                      </Button>
                      <Button
                        v-if="canUpdate"
                        type="link"
                        size="small"
                        @click="openAssignment(asOrgNode(record))"
                      >
                        归属
                      </Button>
                      <Popconfirm
                        v-if="canDelete"
                        title="确认删除或移出该组织？"
                        @confirm="removeNode(asOrgNode(record))"
                      >
                        <Button danger type="link" size="small">删除</Button>
                      </Popconfirm>
                    </Space>
                  </template>
                </template>
              </Table>
            </div>
          </div>

          <div class="data-panel org-lower-panel">
            <div class="list-header">
              <div class="list-title">
                <h2>组织用户</h2>
                <Tag :color="selectedOrgNode ? 'blue' : 'default'">{{ userContextText }}</Tag>
              </div>
              <Space :size="8">
                <Button
                  v-if="canUpdate && selectedOrgNode"
                  @click="openAssignment(selectedOrgNode)"
                >
                  维护归属
                </Button>
                <Tooltip v-if="canQuery && selectedOrgNode" title="刷新用户">
                  <Button shape="circle" @click="loadOrgUsers">
                    <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
                  </Button>
                </Tooltip>
              </Space>
            </div>

            <div class="table-frame">
              <Table
                row-key="assignmentId"
                :columns="userColumns"
                :data-source="orgUsers"
                :loading="usersLoading"
                :pagination="false"
                :scroll="{ x: 1280 }"
                size="small"
                :sticky="{ offsetScroll: 0 }"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'username'">
                    {{ record.username || '-' }}
                  </template>
                  <template v-else-if="column.key === 'positionName'">
                    {{ record.positionName || '-' }}
                  </template>
                  <template v-else-if="column.key === 'primary'">
                    <Tag :color="record.primary ? 'blue' : 'default'">
                      {{ record.primary ? '是' : '否' }}
                    </Tag>
                  </template>
                  <template v-else-if="column.key === 'leader'">
                    <Tag :color="record.leader ? 'purple' : 'default'">
                      {{ record.leader ? '是' : '否' }}
                    </Tag>
                  </template>
                  <template v-else-if="column.key === 'userStatus'">
                    <Tag :color="record.userStatus === 0 ? 'default' : 'green'">
                      {{ record.userStatus === 0 ? '禁用' : '启用' }}
                    </Tag>
                  </template>
                  <template v-else-if="column.key === 'status'">
                    <Tag :color="record.status === 0 ? 'default' : 'green'">
                      {{ record.status === 0 ? '停用' : '有效' }}
                    </Tag>
                  </template>
                  <template v-else-if="column.key === 'email'">
                    {{ record.email || '-' }}
                  </template>
                  <template v-else-if="column.key === 'phone'">
                    {{ record.phone || '-' }}
                  </template>
                </template>
              </Table>
            </div>
          </div>
        </section>
      </section>
    </div>

    <Drawer
      v-model:open="formOpen"
      :title="editingNode ? '修改组织' : '新增组织'"
      placement="right"
      width="720"
    >
      <div class="form-grid">
        <FormItem label="组织编码" required>
          <Input
            v-model:value="formModel.unitCode"
            :disabled="!!editingNode"
            placeholder="请输入组织编码"
          />
        </FormItem>
        <FormItem label="组织名称" required>
          <Input v-model:value="formModel.unitName" placeholder="请输入组织名称" />
        </FormItem>
        <FormItem label="实体类型">
          <Select v-model:value="formModel.unitType" :options="unitTypeOptions" />
        </FormItem>
        <FormItem label="上级组织">
          <Select
            v-model:value="formModel.parentUnitId"
            allow-clear
            show-search
            :filter-option="false"
            :options="parentOptions"
            placeholder="请选择"
          />
        </FormItem>
        <FormItem label="排序">
          <InputNumber v-model:value="formModel.sortOrder" class="w-full" :min="0" />
        </FormItem>
        <FormItem label="状态">
          <Switch v-model:checked="formModel.enabled" checked-children="启用" un-checked-children="禁用" />
        </FormItem>
        <FormItem class="form-wide" label="描述">
          <Textarea v-model:value="formModel.description" :rows="3" placeholder="请输入描述" />
        </FormItem>
      </div>

      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitForm">保存</Button>
        </Space>
      </template>
    </Drawer>

    <Drawer
      v-model:open="assignmentOpen"
      title="用户组织归属"
      placement="right"
      width="720"
    >
      <div class="form-grid">
        <FormItem label="用户ID" required>
          <Input
            v-model:value="userIdForAssignment"
            placeholder="请输入用户ID"
            @press-enter="loadUserAssignments"
          />
        </FormItem>
        <FormItem label="读取归属">
          <Button :disabled="!userIdForAssignment" @click="loadUserAssignments">
            加载已有归属
          </Button>
        </FormItem>
        <FormItem class="form-wide" label="组织范围" required>
          <Select
            v-model:value="assignmentOrgUnitIds"
            mode="multiple"
            :options="parentOptions"
            placeholder="请选择组织"
          />
        </FormItem>
        <FormItem label="主组织">
          <Select
            v-model:value="primaryOrgUnitId"
            allow-clear
            :options="
              parentOptions.filter((item) => assignmentOrgUnitIds.includes(String(item.value)))
            "
          />
        </FormItem>
        <FormItem label="岗位">
          <Input v-model:value="assignmentPosition" placeholder="例如：部门负责人" />
        </FormItem>
      </div>

      <template #footer>
        <Space>
          <Button @click="assignmentOpen = false">取消</Button>
          <Button type="primary" @click="submitAssignment">保存</Button>
        </Space>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.org-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 8px;
}

.org-workbench {
  display: flex;
  flex: 1;
  gap: 8px;
  min-height: 0;
}

.org-tree-panel {
  display: flex;
  flex: 0 0 260px;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.org-tree-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #edf0f5;
}

.org-tree-header h3 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.org-tree-header span {
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.org-tree-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
}

.dimension-select {
  min-width: 0;
  flex: 1;
}

.org-tree-panel :deep(.ant-tree) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: transparent;
  font-size: 13px;
}

.org-tree-panel :deep(.ant-tree-treenode) {
  align-items: center;
  width: 100%;
  padding-bottom: 2px;
}

.org-tree-panel :deep(.ant-tree-node-content-wrapper) {
  min-height: 26px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 26px;
  border-radius: 4px;
}

.org-tree-panel :deep(.ant-tree-node-selected) {
  font-weight: 600;
}

.org-tree-hint {
  flex: 0 0 auto;
  margin-top: 6px;
  padding-top: 6px;
  color: #6b7280;
  border-top: 1px solid #edf0f5;
  font-size: 12px;
  line-height: 18px;
}

.org-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  width: 0;
  min-height: 0;
}

.org-upper-panel,
.org-lower-panel {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-height: 0;
}

.org-upper-panel {
  flex-basis: 46%;
}

.org-lower-panel {
  flex-basis: 54%;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.list-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.list-title h2 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.table-frame {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.table-frame :deep(.is-current-org-row > td) {
  background: rgb(230 244 255 / 70%) !important;
  font-weight: 600;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.form-wide {
  grid-column: 1 / -1;
}

@media (max-width: 720px) {
  .org-workbench {
    flex-direction: column;
  }

  .org-tree-panel {
    flex: 0 0 auto;
    max-height: 260px;
  }

  .org-table-panel {
    width: 100%;
  }

  .org-upper-panel,
  .org-lower-panel {
    flex-basis: auto;
    min-height: 260px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
