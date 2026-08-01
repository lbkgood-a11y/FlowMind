<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api';

import { computed, inject, reactive, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Drawer,
  Form,
  FormItem,
  Input,
  message,
  Popconfirm,
  Select,
  Space,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  deleteAuthorizationFieldPolicy,
  getRoleAuthorizationProfile,
  saveAuthorizationFieldPolicy,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import { ClientPaginatedTable } from '#/shared';

const ctx = inject<any>('authzContext')!;

const fieldDrawerOpen = ref(false);
const fieldForm = reactive({
  resourceCode: '',
  fieldKey: '',
  subjectType: 'ROLE' as string,
  subjectId: '',
  readMode: '',
  writeMode: '',
  maskStrategy: '',
  description: '',
});
const fieldRows = ref<SystemAuthorizationApi.FieldPolicy[]>([]);

const readModeOptions = computed(() =>
  (ctx.adminOptions.value?.fieldReadModes ?? []).map((o: any) => ({ label: o.label || o.code, value: o.code })),
);
const writeModeOptions = computed(() =>
  (ctx.adminOptions.value?.fieldWriteModes ?? []).map((o: any) => ({ label: o.label || o.code, value: o.code })),
);
const maskStrategyOptions = computed(() =>
  (ctx.adminOptions.value?.maskStrategies ?? []).map((o: any) => ({ label: o.label || o.code, value: o.code })),
);

const fieldColumns: TableProps['columns'] = [
  { title: '资源', dataIndex: 'resourceCode', key: 'resourceCode', width: 200 },
  { title: '字段', dataIndex: 'fieldKey', key: 'fieldKey', width: 140 },
  { title: '主体', dataIndex: 'subjectId', key: 'subjectId', width: 140 },
  { title: '读取模式', dataIndex: 'readMode', key: 'readMode', width: 110 },
  { title: '写入模式', dataIndex: 'writeMode', key: 'writeMode', width: 110 },
  { title: '脱敏策略', dataIndex: 'maskStrategy', key: 'maskStrategy', width: 120 },
  { title: '操作', key: 'action', width: 100 },
];

function asStringValue(value: unknown) {
  return typeof value === 'string' ? value : String(value ?? '');
}

function changeFieldResource(value: unknown) {
  fieldForm.resourceCode = asStringValue(value);
  fieldForm.fieldKey = '';
}

function openFieldDrawer() {
  const first = ctx.fieldResourceOptions.value[0];
  fieldForm.resourceCode = first?.value ?? '';
  fieldForm.fieldKey = '';
  fieldForm.subjectType = 'ROLE';
  fieldForm.subjectId = ctx.selectedRoleId.value || '';
  fieldForm.readMode = ctx.adminOptions.value?.fieldReadModes?.[0]?.code ?? 'VISIBLE';
  fieldForm.writeMode = ctx.adminOptions.value?.fieldWriteModes?.[0]?.code ?? 'EDITABLE';
  fieldForm.maskStrategy = '';
  fieldForm.description = '';
  fieldDrawerOpen.value = true;
}

async function handleSaveField() {
  if (!fieldForm.resourceCode || !fieldForm.fieldKey || !fieldForm.subjectId) {
    message.warning('请填写完整信息');
    return;
  }
  ctx.saving.value = true;
  try {
    await saveAuthorizationFieldPolicy({
      effect: 'ALLOW',
      resourceCode: fieldForm.resourceCode,
      fieldKey: fieldForm.fieldKey,
      subjectType: fieldForm.subjectType as 'ROLE' | 'USER',
      subjectId: fieldForm.subjectId,
      readMode: fieldForm.readMode,
      writeMode: fieldForm.writeMode,
      maskStrategy: fieldForm.maskStrategy || undefined,
    });
    message.success('字段策略已保存');
    fieldDrawerOpen.value = false;
    await loadFieldPolicies();
  } catch {
    message.error('保存字段策略失败');
  } finally {
    ctx.saving.value = false;
  }
}

async function handleDeleteField(record: SystemAuthorizationApi.FieldPolicy) {
  if (!record.id) return;
  try {
    await deleteAuthorizationFieldPolicy(record.id);
    message.success('字段策略已删除');
    await loadFieldPolicies();
  } catch {
    message.error('删除字段策略失败');
  }
}

async function loadFieldPolicies() {
  if (!ctx.canQuery.value || !ctx.selectedRoleId.value) {
    fieldRows.value = [];
    return;
  }
  try {
    const profile = await getRoleAuthorizationProfile(ctx.selectedRoleId.value);
    fieldRows.value = profile.fieldPolicies ?? [];
  } catch {
    fieldRows.value = [];
  }
}

function readModeColor(mode?: string) {
  if (mode === 'VISIBLE') return 'success';
  if (mode === 'MASKED') return 'warning';
  return 'error';
}

watch(() => ctx.selectedRoleId.value, () => {
  fieldRows.value = [];
});
</script>

<template>
  <div>
    <div class="mb-3 flex items-center justify-between">
      <span class="text-muted-foreground text-sm">按字段配置读写权限和脱敏策略</span>
      <Space v-if="ctx.canCreate.value">
        <Tooltip title="刷新">
          <Button shape="circle" @click="loadFieldPolicies">
            <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
          </Button>
        </Tooltip>
        <Button type="primary" @click="openFieldDrawer">
          <IconifyIcon icon="lucide:plus" class="mr-1" />新增字段策略
        </Button>
      </Space>
    </div>
    <ClientPaginatedTable
        :columns="fieldColumns"
        :data-source="fieldRows"
        :loading="ctx.loading.value"
        row-key="id"
      >
        <template #bodyCell="{ column, record }: any">
          <template v-if="column.key === 'readMode'">
            <Tag :color="readModeColor(record.readMode)">{{ record.readMode }}</Tag>
          </template>
          <template v-else-if="column.key === 'action' && ctx.canDelete.value">
            <Popconfirm title="确认删除?" @confirm="handleDeleteField(record)">
              <Button type="link" danger size="small" @click.stop>删除</Button>
            </Popconfirm>
          </template>
        </template>
    </ClientPaginatedTable>

    <Drawer v-model:open="fieldDrawerOpen" title="新增字段策略" :width="500">
      <Form layout="vertical">
        <FormItem label="资源">
          <Select v-model:value="fieldForm.resourceCode" :options="ctx.fieldResourceOptions.value" @change="changeFieldResource" />
        </FormItem>
        <FormItem label="字段">
          <Select v-model:value="fieldForm.fieldKey" :options="ctx.fieldOptionsForResource(fieldForm.resourceCode)" />
        </FormItem>
        <FormItem label="主体类型">
          <Select v-model:value="fieldForm.subjectType" :options="[{ label: '角色 (ROLE)', value: 'ROLE' }, { label: '用户 (USER)', value: 'USER' }]" />
        </FormItem>
        <FormItem label="主体ID">
          <Input v-model:value="fieldForm.subjectId" placeholder="输入角色编码或用户ID" />
        </FormItem>
        <FormItem label="读取模式">
          <Select v-model:value="fieldForm.readMode" :options="readModeOptions" />
        </FormItem>
        <FormItem label="写入模式">
          <Select v-model:value="fieldForm.writeMode" :options="writeModeOptions" />
        </FormItem>
        <FormItem label="脱敏策略">
          <Select v-model:value="fieldForm.maskStrategy" :options="maskStrategyOptions" allow-clear />
        </FormItem>
        <FormItem label="描述">
          <Input.TextArea v-model:value="fieldForm.description" :rows="2" />
        </FormItem>
      </Form>
      <template #footer>
        <Button @click="fieldDrawerOpen = false">取消</Button>
        <Button type="primary" :loading="ctx.saving.value" @click="handleSaveField">保存</Button>
      </template>
    </Drawer>
  </div>
</template>
