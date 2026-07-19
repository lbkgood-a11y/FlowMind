<script setup lang="ts">
import type { SystemOrgApi, SystemUserApi } from '#/api';
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
  Radio,
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
  getUserList,
  saveOrgRelation,
  updateOrgUnit,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import {
  BusinessPageScaffold,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

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
const selectedAssignmentUser = ref<SystemUserApi.SystemUser>();
const userSearchLoading = ref(false);
const userSearchOptions = ref<Array<{ label: string; value: string }>>([]);
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
const selectedAssignmentOrgOptions = computed(() =>
  parentOptions.value.filter((item) =>
    assignmentOrgUnitIds.value.includes(String(item.value)),
  ),
);

let userSearchSequence = 0;
let userSearchTimer: ReturnType<typeof setTimeout> | undefined;

function formatUserOption(user: SystemUserApi.SystemUser) {
  const contacts = [user.email, user.phone].filter(Boolean).join(' · ');
  return {
    label: contacts ? `${user.username}（${contacts}）` : user.username,
    value: user.id,
  };
}

async function searchAssignmentUsers(keyword = '') {
  const sequence = ++userSearchSequence;
  userSearchLoading.value = true;
  try {
    const result = await getUserList({ keyword: keyword.trim() || undefined, page: 1, size: 20 });
    if (sequence !== userSearchSequence) {
      return;
    }
    userSearchOptions.value = result.items.map(formatUserOption);
  } finally {
    if (sequence === userSearchSequence) {
      userSearchLoading.value = false;
    }
  }
}

function handleUserSearch(keyword: string) {
  if (userSearchTimer) {
    clearTimeout(userSearchTimer);
  }
  userSearchTimer = setTimeout(() => searchAssignmentUsers(keyword), 300);
}

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
  selectedAssignmentUser.value = undefined;
  userSearchOptions.value = [];
  assignmentOrgUnitIds.value = [record.id];
  primaryOrgUnitId.value = record.id;
  assignmentPosition.value = '';
  assignmentOpen.value = true;
  await searchAssignmentUsers();
}

async function loadUserAssignments() {
  const userId = userIdForAssignment.value.trim();
  if (!userId) {
    message.warning('请先选择用户');
    return;
  }
  const assignments = await getUserOrgAssignments(userId, activeDimension.value);
  if (assignments.length > 0) {
    assignmentOrgUnitIds.value = assignments.map((item) => item.orgUnitId);
    primaryOrgUnitId.value =
      assignments.find((item) => item.primary)?.orgUnitId ?? assignments[0]?.orgUnitId;
    assignmentPosition.value = assignments[0]?.positionName ?? '';
    return;
  }

  assignmentOrgUnitIds.value = selectedOrgUnitId.value ? [selectedOrgUnitId.value] : [];
  primaryOrgUnitId.value = assignmentOrgUnitIds.value[0];
  assignmentPosition.value = '';
}

async function handleAssignmentUserChange(userValue: unknown) {
  const userId =
    typeof userValue === 'string' || typeof userValue === 'number'
      ? String(userValue)
      : undefined;
  selectedAssignmentUser.value = undefined;
  if (!userId) {
    assignmentOrgUnitIds.value = selectedOrgUnitId.value ? [selectedOrgUnitId.value] : [];
    primaryOrgUnitId.value = assignmentOrgUnitIds.value[0];
    assignmentPosition.value = '';
    return;
  }

  const result = await getUserList({ page: 1, size: 1, userId });
  selectedAssignmentUser.value = result.items[0];
  await loadUserAssignments();
}

async function submitAssignment() {
  if (!canUpdate.value) {
    message.warning('当前账号没有维护用户组织归属的权限');
    return;
  }
  if (!userIdForAssignment.value.trim()) {
    message.warning('请先选择用户');
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

watch(
  assignmentOrgUnitIds,
  (orgUnitIds) => {
    if (!orgUnitIds.includes(primaryOrgUnitId.value ?? '')) {
      primaryOrgUnitId.value = orgUnitIds[0];
    }
  },
  { deep: true },
);

onMounted(async () => {
  await loadDimensions();
  await loadTree();
});
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="org-page" pattern="master-detail">
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
            <CompactToolbar>
              <template #title>
                <div class="list-title">
                <h2>组织列表</h2>
                <Tag color="blue">{{ tableContextText }}</Tag>
              </div>
              </template>
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
            </CompactToolbar>

            <CompactTableFrame>
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
            </CompactTableFrame>
          </div>

          <div class="data-panel org-lower-panel">
            <CompactToolbar>
              <template #title>
                <div class="list-title">
                <h2>组织用户</h2>
                <Tag :color="selectedOrgNode ? 'blue' : 'default'">{{ userContextText }}</Tag>
              </div>
              </template>
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
            </CompactToolbar>

            <CompactTableFrame>
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
            </CompactTableFrame>
          </div>
        </section>
      </section>
    </BusinessPageScaffold>

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
      title="配置用户组织归属"
      placement="right"
      width="720"
    >
      <div class="assignment-guide">
        先搜索并确认要配置的用户，再设置该用户在“{{ activeDimensionName }}”中的组织归属。
      </div>

      <section class="assignment-section">
        <div class="assignment-section-title">
          <span class="assignment-step">1</span>
          <div>
            <strong>选择用户</strong>
            <p>支持按用户名、手机号或邮箱搜索，选择后自动加载已有归属。</p>
          </div>
        </div>
        <FormItem label="用户" required>
          <Select
            v-model:value="userIdForAssignment"
            allow-clear
            show-search
            :filter-option="false"
            :loading="userSearchLoading"
            :options="userSearchOptions"
            placeholder="搜索用户名、手机号或邮箱"
            @change="handleAssignmentUserChange"
            @search="handleUserSearch"
          />
        </FormItem>

        <div v-if="selectedAssignmentUser" class="selected-user-card">
          <div>
            <strong>{{ selectedAssignmentUser.username }}</strong>
            <Tag :color="selectedAssignmentUser.status === 1 ? 'green' : 'default'">
              {{ selectedAssignmentUser.status === 1 ? '启用' : '停用' }}
            </Tag>
          </div>
          <span>
            {{ selectedAssignmentUser.email || '未填写邮箱' }} ·
            {{ selectedAssignmentUser.phone || '未填写手机号' }}
          </span>
        </div>
      </section>

      <section class="assignment-section" :class="{ 'is-disabled': !userIdForAssignment }">
        <div class="assignment-section-title">
          <span class="assignment-step">2</span>
          <div>
            <strong>配置组织</strong>
            <p>选择用户所属的一个或多个组织。</p>
          </div>
        </div>
        <FormItem label="归属组织" required>
          <Select
            v-model:value="assignmentOrgUnitIds"
            :disabled="!userIdForAssignment"
            mode="multiple"
            :options="parentOptions"
            placeholder="请选择归属组织"
            show-search
          />
        </FormItem>

        <FormItem label="主组织" required>
          <Radio.Group
            v-model:value="primaryOrgUnitId"
            class="primary-org-options"
            :disabled="!userIdForAssignment || assignmentOrgUnitIds.length === 0"
          >
            <Radio
              v-for="option in selectedAssignmentOrgOptions"
              :key="String(option.value)"
              :value="String(option.value)"
            >
              {{ option.label }}
            </Radio>
          </Radio.Group>
          <div v-if="assignmentOrgUnitIds.length === 0" class="field-hint">
            请先选择归属组织；首个组织会自动设为主组织。
          </div>
        </FormItem>

        <FormItem label="岗位">
          <Input
            v-model:value="assignmentPosition"
            :disabled="!userIdForAssignment"
            placeholder="例如：部门负责人（选填）"
          />
        </FormItem>
      </section>

      <template #footer>
        <Space>
          <Button @click="assignmentOpen = false">取消</Button>
          <Button
            type="primary"
            :disabled="!userIdForAssignment || assignmentOrgUnitIds.length === 0"
            @click="submitAssignment"
          >
            保存归属
          </Button>
        </Space>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.org-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 100%;
}

.org-workbench {
  display: flex;
  flex: 1;
  gap: var(--erp-panel-gap);
  min-height: 0;
}

.org-tree-panel {
  display: flex;
  flex: 0 0 var(--erp-master-panel-width);
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.org-tree-header {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 6px;
  border-bottom: 1px solid #edf0f5;
}

.org-tree-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  color: #111827;
}

.org-tree-header span {
  font-size: 12px;
  line-height: 18px;
  color: #6b7280;
}

.org-tree-toolbar {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 6px 0;
}

.dimension-select {
  flex: 1;
  min-width: 0;
}

.org-tree-panel :deep(.ant-tree) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  font-size: 13px;
  background: transparent;
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
  line-height: 26px;
  white-space: nowrap;
  border-radius: 4px;
}

.org-tree-panel :deep(.ant-tree-node-selected) {
  font-weight: 600;
}

.org-tree-hint {
  flex: 0 0 auto;
  padding-top: 6px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
  color: #6b7280;
  border-top: 1px solid #edf0f5;
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

.table-frame {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.table-frame :deep(.is-current-org-row > td) {
  font-weight: 600;
  background: rgb(230 244 255 / 70%) !important;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.form-wide {
  grid-column: 1 / -1;
}

.assignment-guide {
  padding: 10px 12px;
  margin-bottom: 16px;
  font-size: 13px;
  line-height: 20px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.assignment-section {
  padding: 16px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.assignment-section.is-disabled {
  background: #fafafa;
}

.assignment-section-title {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.assignment-section-title strong {
  font-size: 14px;
  color: #111827;
}

.assignment-section-title p {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: #6b7280;
}

.assignment-step {
  display: inline-flex;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #1677ff;
  border-radius: 50%;
}

.selected-user-card {
  padding: 10px 12px;
  margin-left: 92px;
  font-size: 12px;
  color: #64748b;
  background: #f0f7ff;
  border: 1px solid #bae0ff;
  border-radius: 6px;
}

.selected-user-card > div {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 4px;
  font-size: 14px;
  color: #111827;
}

.primary-org-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-hint {
  font-size: 12px;
  line-height: 20px;
  color: #94a3b8;
}

@media (max-width: 720px) {
  .org-workbench {
    flex-direction: column;
  }

  .org-tree-panel {
    flex: 0 0 auto;
    max-height: 260px;
  }

  .selected-user-card {
    margin-left: 0;
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
