<script setup lang="ts">
import type { SystemGovernanceApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  Button,
  Drawer,
  Empty,
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
} from 'ant-design-vue';

import {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  getDictItems,
  getDictTypes,
  updateDictItem,
  updateDictType,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

const Textarea = Input.TextArea;

const DICT_PERMISSIONS = {
  create: '/api/v1/dictionaries:POST',
  delete: '/api/v1/dictionaries/*:DELETE',
  query: '/api/v1/dictionaries:GET',
  update: '/api/v1/dictionaries/*:PUT',
} as const;

type DictTypeFormModel = {
  description?: string;
  dictCode: string;
  dictName: string;
  sortOrder: number;
  status: 0 | 1;
};

type DictItemFormModel = {
  cssClass?: string;
  description?: string;
  itemLabel: string;
  itemValue: string;
  metadata?: string;
  sortOrder: number;
  status: 0 | 1;
  tagType?: string;
};

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([DICT_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([DICT_PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([DICT_PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([DICT_PERMISSIONS.delete]));

const types = ref<SystemGovernanceApi.DictType[]>([]);
const items = ref<SystemGovernanceApi.DictItem[]>([]);
const selectedTypeId = ref<string>();
const typeLoading = ref(false);
const itemLoading = ref(false);
const saving = ref(false);
const typeFormOpen = ref(false);
const itemFormOpen = ref(false);
const editingType = ref<SystemGovernanceApi.DictType>();
const editingItem = ref<SystemGovernanceApi.DictItem>();

const typeQuery = reactive({
  keyword: '',
  status: undefined as 0 | 1 | undefined,
});

const itemQuery = reactive({
  status: undefined as 0 | 1 | undefined,
});

const typeForm = reactive<DictTypeFormModel>({
  description: '',
  dictCode: '',
  dictName: '',
  sortOrder: 100,
  status: 1,
});

const itemForm = reactive<DictItemFormModel>({
  cssClass: '',
  description: '',
  itemLabel: '',
  itemValue: '',
  metadata: '',
  sortOrder: 100,
  status: 1,
  tagType: '',
});

const selectedType = computed(() =>
  types.value.find((item) => item.id === selectedTypeId.value),
);

const typeRowSelection = computed<TableProps['rowSelection']>(() => ({
  columnWidth: 38,
  hideSelectAll: true,
  selectedRowKeys: selectedTypeId.value ? [selectedTypeId.value] : [],
  type: 'checkbox',
  onSelect: (record, selected) => {
    const row = record as SystemGovernanceApi.DictType;
    if (selected) {
      selectType(row);
      return;
    }
    if (selectedTypeId.value === row.id) {
      selectedTypeId.value = undefined;
      items.value = [];
    }
  },
}));

const typeColumns = computed<TableProps['columns']>(() => [
  { dataIndex: 'dictName', fixed: 'left', key: 'dictName', title: '字典名称', width: 150 },
  { dataIndex: 'dictCode', key: 'dictCode', title: '字典编码', width: 190 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 90 },
  { dataIndex: 'systemFlag', key: 'systemFlag', title: '内置', width: 80 },
  { dataIndex: 'sortOrder', key: 'sortOrder', title: '排序', width: 80 },
  { dataIndex: 'description', key: 'description', title: '描述', width: 220 },
  { fixed: 'right', key: 'action', title: '操作', width: 150 },
]);

const itemColumns = computed<TableProps['columns']>(() => [
  { dataIndex: 'itemLabel', fixed: 'left', key: 'itemLabel', title: '标签', width: 150 },
  { dataIndex: 'itemValue', key: 'itemValue', title: '值', width: 150 },
  { dataIndex: 'tagType', key: 'tagType', title: '标签类型', width: 120 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 90 },
  { dataIndex: 'systemFlag', key: 'systemFlag', title: '内置', width: 80 },
  { dataIndex: 'sortOrder', key: 'sortOrder', title: '排序', width: 80 },
  { dataIndex: 'description', key: 'description', title: '描述', width: 220 },
  { fixed: 'right', key: 'action', title: '操作', width: 150 },
]);

async function loadTypes() {
  if (!canQuery.value) {
    types.value = [];
    selectedTypeId.value = undefined;
    return;
  }
  typeLoading.value = true;
  try {
    types.value = await getDictTypes({
      keyword: typeQuery.keyword || undefined,
      status: typeQuery.status,
    });
    if (!selectedTypeId.value || !types.value.some((item) => item.id === selectedTypeId.value)) {
      selectedTypeId.value = types.value[0]?.id;
    }
  } finally {
    typeLoading.value = false;
  }
}

async function loadItems() {
  if (!canQuery.value || !selectedType.value) {
    items.value = [];
    return;
  }
  itemLoading.value = true;
  try {
    items.value = await getDictItems({
      dictCode: selectedType.value.dictCode,
      status: itemQuery.status,
    });
  } finally {
    itemLoading.value = false;
  }
}

function resetTypeQuery() {
  typeQuery.keyword = '';
  typeQuery.status = undefined;
  loadTypes();
}

function resetItemQuery() {
  itemQuery.status = undefined;
  loadItems();
}

function openCreateType() {
  editingType.value = undefined;
  typeForm.dictCode = '';
  typeForm.dictName = '';
  typeForm.status = 1;
  typeForm.sortOrder = 100;
  typeForm.description = '';
  typeFormOpen.value = true;
}

function openEditType(record: SystemGovernanceApi.DictType) {
  editingType.value = record;
  typeForm.dictCode = record.dictCode;
  typeForm.dictName = record.dictName;
  typeForm.status = (record.status ?? 1) as 0 | 1;
  typeForm.sortOrder = record.sortOrder ?? 100;
  typeForm.description = record.description ?? '';
  typeFormOpen.value = true;
}

function openCreateItem() {
  if (!selectedType.value) {
    message.warning('请先选择字典类型');
    return;
  }
  editingItem.value = undefined;
  itemForm.itemLabel = '';
  itemForm.itemValue = '';
  itemForm.tagType = '';
  itemForm.cssClass = '';
  itemForm.status = 1;
  itemForm.sortOrder = 100;
  itemForm.description = '';
  itemForm.metadata = '';
  itemFormOpen.value = true;
}

function openEditItem(record: SystemGovernanceApi.DictItem) {
  editingItem.value = record;
  itemForm.itemLabel = record.itemLabel;
  itemForm.itemValue = record.itemValue;
  itemForm.tagType = record.tagType ?? '';
  itemForm.cssClass = record.cssClass ?? '';
  itemForm.status = (record.status ?? 1) as 0 | 1;
  itemForm.sortOrder = record.sortOrder ?? 100;
  itemForm.description = record.description ?? '';
  itemForm.metadata = record.metadata ?? '';
  itemFormOpen.value = true;
}

async function submitType() {
  if (!canUpdate.value && editingType.value) {
    message.warning('当前账号没有修改字典的权限');
    return;
  }
  if (!canCreate.value && !editingType.value) {
    message.warning('当前账号没有新增字典的权限');
    return;
  }
  if (!typeForm.dictCode.trim() || !typeForm.dictName.trim()) {
    message.warning('请输入字典编码和名称');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      description: typeForm.description?.trim() || undefined,
      dictCode: typeForm.dictCode.trim(),
      dictName: typeForm.dictName.trim(),
      sortOrder: typeForm.sortOrder,
      status: typeForm.status,
      systemFlag: editingType.value?.systemFlag ?? 0,
    };
    if (editingType.value) {
      await updateDictType(editingType.value.id, payload);
      message.success('字典类型已更新');
    } else {
      await createDictType(payload);
      message.success('字典类型已创建');
    }
    typeFormOpen.value = false;
    await loadTypes();
    await loadItems();
  } finally {
    saving.value = false;
  }
}

async function submitItem() {
  if (!selectedType.value) {
    message.warning('请先选择字典类型');
    return;
  }
  if (!canUpdate.value && editingItem.value) {
    message.warning('当前账号没有修改字典项的权限');
    return;
  }
  if (!canCreate.value && !editingItem.value) {
    message.warning('当前账号没有新增字典项的权限');
    return;
  }
  if (!itemForm.itemLabel.trim() || !itemForm.itemValue.trim()) {
    message.warning('请输入字典项标签和值');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      cssClass: itemForm.cssClass?.trim() || undefined,
      description: itemForm.description?.trim() || undefined,
      dictCode: selectedType.value.dictCode,
      dictTypeId: selectedType.value.id,
      itemLabel: itemForm.itemLabel.trim(),
      itemValue: itemForm.itemValue.trim(),
      metadata: itemForm.metadata?.trim() || undefined,
      sortOrder: itemForm.sortOrder,
      status: itemForm.status,
      systemFlag: editingItem.value?.systemFlag ?? 0,
      tagType: itemForm.tagType?.trim() || undefined,
    };
    if (editingItem.value) {
      await updateDictItem(editingItem.value.id, payload);
      message.success('字典项已更新');
    } else {
      await createDictItem(payload);
      message.success('字典项已创建');
    }
    itemFormOpen.value = false;
    await loadItems();
  } finally {
    saving.value = false;
  }
}

async function removeType(record: SystemGovernanceApi.DictType) {
  if (record.systemFlag === 1) {
    message.warning('系统内置字典不能删除，可改为禁用');
    return;
  }
  await deleteDictType(record.id);
  message.success('字典类型已删除');
  await loadTypes();
  await loadItems();
}

async function removeItem(record: SystemGovernanceApi.DictItem) {
  if (record.systemFlag === 1) {
    message.warning('系统内置字典项不能删除，可改为禁用');
    return;
  }
  await deleteDictItem(record.id);
  message.success('字典项已删除');
  await loadItems();
}

function asType(record: Record<string, any>) {
  return record as SystemGovernanceApi.DictType;
}

function asItem(record: Record<string, any>) {
  return record as SystemGovernanceApi.DictItem;
}

function typeRowClassName(record: SystemGovernanceApi.DictType) {
  return record.id === selectedTypeId.value ? 'selected-row' : '';
}

function selectType(record: SystemGovernanceApi.DictType) {
  if (selectedTypeId.value === record.id) {
    loadItems();
    return;
  }
  selectedTypeId.value = record.id;
}

function typeRow(record: SystemGovernanceApi.DictType) {
  return {
    onClick: () => {
      selectType(record);
    },
  };
}

watch(selectedTypeId, () => {
  loadItems();
});

onMounted(async () => {
  await loadTypes();
  await loadItems();
});
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="dictionary-page" pattern="master-detail">
      <template #query>
        <CompactQueryBar :columns="3">
          <Input v-model:value="typeQuery.keyword" class="query-input" placeholder="字典编码/名称" allow-clear />
          <Select
            v-model:value="typeQuery.status"
            allow-clear
            class="query-select"
            :options="[
              { label: '启用', value: 1 },
              { label: '禁用', value: 0 },
            ]"
            placeholder="类型状态"
          />
          <template #actions>
            <Button v-if="canQuery" type="primary" @click="loadTypes">查询</Button>
            <Button v-if="canQuery" @click="resetTypeQuery">重置</Button>
            <Tooltip v-if="canQuery" title="刷新">
              <Button shape="circle" @click="loadTypes">
                <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
              </Button>
            </Tooltip>
            <Button v-if="canCreate" type="primary" @click="openCreateType">
              <Plus class="size-4" />
              新增类型
            </Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar title="字典管理" subtitle="维护字典类型、字典项和内置状态" />
      </template>

      <div class="dictionary-layout">
        <section class="data-panel type-panel">
          <div class="panel-header">
            <div class="panel-title">字典列表</div>
            <div class="panel-meta">点击字典行查看下方字典项</div>
          </div>
          <CompactTableFrame>
            <Table
            row-key="id"
            :columns="typeColumns"
            :data-source="types"
            :loading="typeLoading"
            :pagination="false"
            :custom-row="typeRow"
            :row-selection="typeRowSelection"
            :row-class-name="typeRowClassName"
            :scroll="{ x: 960 }"
            size="small"
            :sticky="{ offsetScroll: 0 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <Tag :color="record.status === 1 ? 'green' : 'default'">
                  {{ record.status === 1 ? '启用' : '禁用' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'systemFlag'">
                <Tag :color="record.systemFlag === 1 ? 'blue' : 'default'">
                  {{ record.systemFlag === 1 ? '是' : '否' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Space>
                  <Button v-if="canUpdate" type="link" size="small" @click.stop="openEditType(asType(record))">
                    编辑
                  </Button>
                  <Popconfirm
                    v-if="canDelete"
                    title="确认删除该字典类型？"
                    @confirm="removeType(asType(record))"
                  >
                    <Button danger type="link" size="small" @click.stop>删除</Button>
                  </Popconfirm>
                </Space>
              </template>
            </template>
          </Table>
          </CompactTableFrame>
        </section>

        <section class="data-panel item-panel">
          <div class="panel-header item-toolbar">
            <Space wrap>
              <span class="panel-title">字典项列表</span>
              <span class="current-type">{{ selectedType?.dictName || '未选择字典类型' }}</span>
              <Select
                v-model:value="itemQuery.status"
                allow-clear
                class="query-select"
                :options="[
                  { label: '启用', value: 1 },
                  { label: '禁用', value: 0 },
                ]"
                placeholder="字典项状态"
              />
              <Button v-if="canQuery" @click="loadItems">查询</Button>
              <Button v-if="canQuery" @click="resetItemQuery">重置</Button>
              <Button v-if="canCreate" type="primary" :disabled="!selectedType" @click="openCreateItem">
                <Plus class="size-4" />
                新增字典项
              </Button>
            </Space>
          </div>

          <Empty v-if="!selectedType" description="请选择上方字典类型" />
          <CompactTableFrame v-else>
            <Table
            row-key="id"
            :columns="itemColumns"
            :data-source="items"
            :loading="itemLoading"
            :pagination="false"
            :scroll="{ x: 960 }"
            size="small"
            :sticky="{ offsetScroll: 0 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <Tag :color="record.status === 1 ? 'green' : 'default'">
                  {{ record.status === 1 ? '启用' : '禁用' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'systemFlag'">
                <Tag :color="record.systemFlag === 1 ? 'blue' : 'default'">
                  {{ record.systemFlag === 1 ? '是' : '否' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Space>
                  <Button v-if="canUpdate" type="link" size="small" @click="openEditItem(asItem(record))">
                    编辑
                  </Button>
                  <Popconfirm
                    v-if="canDelete"
                    title="确认删除该字典项？"
                    @confirm="removeItem(asItem(record))"
                  >
                    <Button danger type="link" size="small">删除</Button>
                  </Popconfirm>
                </Space>
              </template>
            </template>
          </Table>
          </CompactTableFrame>
        </section>
      </div>
    </BusinessPageScaffold>

    <Drawer
      v-model:open="typeFormOpen"
      :title="editingType ? '编辑字典类型' : '新增字典类型'"
      placement="right"
      width="680"
    >
      <div class="form-grid">
        <FormItem label="字典编码" required>
          <Input v-model:value="typeForm.dictCode" placeholder="例如：USER_STATUS" />
        </FormItem>
        <FormItem label="字典名称" required>
          <Input v-model:value="typeForm.dictName" placeholder="请输入字典名称" />
        </FormItem>
        <FormItem label="排序">
          <InputNumber v-model:value="typeForm.sortOrder" class="w-full" :min="0" />
        </FormItem>
        <FormItem label="状态">
          <Switch
            v-model:checked="typeForm.status"
            :checked-value="1"
            :un-checked-value="0"
            checked-children="启用"
            un-checked-children="禁用"
          />
        </FormItem>
        <FormItem class="form-wide" label="描述">
          <Textarea v-model:value="typeForm.description" :rows="3" placeholder="请输入描述" />
        </FormItem>
      </div>

      <template #footer>
        <Space>
          <Button @click="typeFormOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitType">保存</Button>
        </Space>
      </template>
    </Drawer>

    <Drawer
      v-model:open="itemFormOpen"
      :title="editingItem ? '编辑字典项' : '新增字典项'"
      placement="right"
      width="760"
    >
      <div class="form-grid">
        <FormItem label="所属字典">
          <Input :value="selectedType?.dictName" disabled />
        </FormItem>
        <FormItem label="标签" required>
          <Input v-model:value="itemForm.itemLabel" placeholder="请输入标签" />
        </FormItem>
        <FormItem label="值" required>
          <Input v-model:value="itemForm.itemValue" placeholder="请输入值" />
        </FormItem>
        <FormItem label="标签类型">
          <Input v-model:value="itemForm.tagType" placeholder="例如：success / warning" />
        </FormItem>
        <FormItem label="CSS 类">
          <Input v-model:value="itemForm.cssClass" placeholder="可选" />
        </FormItem>
        <FormItem label="排序">
          <InputNumber v-model:value="itemForm.sortOrder" class="w-full" :min="0" />
        </FormItem>
        <FormItem label="状态">
          <Switch
            v-model:checked="itemForm.status"
            :checked-value="1"
            :un-checked-value="0"
            checked-children="启用"
            un-checked-children="禁用"
          />
        </FormItem>
        <FormItem class="form-wide" label="描述">
          <Textarea v-model:value="itemForm.description" :rows="2" placeholder="请输入描述" />
        </FormItem>
        <FormItem class="form-wide" label="元数据">
          <Textarea v-model:value="itemForm.metadata" :rows="3" placeholder='例如：{"color":"green"}' />
        </FormItem>
      </div>

      <template #footer>
        <Space>
          <Button @click="itemFormOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitItem">保存</Button>
        </Space>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.dictionary-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 8px;
}

.toolbar {
  width: 100%;
}

.dictionary-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 8px;
}

.data-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: auto;
}

.type-panel {
  flex: 0 0 42%;
}

.type-panel :deep(.ant-table-tbody > tr) {
  cursor: pointer;
}

.item-panel {
  flex: 1 1 0;
}

.panel-header {
  display: flex;
  min-height: 30px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.panel-title {
  color: hsl(var(--foreground));
  font-size: 13px;
  font-weight: 700;
}

.panel-meta {
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}

.item-toolbar {
  margin-bottom: 6px;
}

.query-input {
  width: 190px;
}

.query-select {
  width: 130px;
}

.current-type {
  display: inline-flex;
  max-width: 220px;
  align-items: center;
  overflow: hidden;
  color: hsl(var(--foreground));
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-panel :deep(.ant-table-wrapper) {
  min-height: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.form-wide {
  grid-column: 1 / -1;
}

:deep(.selected-row > td) {
  background: hsl(var(--accent));
}

@media (max-width: 1080px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .type-panel {
    flex-basis: auto;
  }
}
</style>
