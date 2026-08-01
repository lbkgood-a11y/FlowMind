<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api';

import { inject, reactive, ref, watch } from 'vue';

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
  deleteAuthorizationGrant,
  getRoleAuthorizationProfile,
  saveAuthorizationGrant,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import { ClientPaginatedTable } from '#/shared';

const ctx = inject<any>('authzContext')!;

const grantDrawerOpen = ref(false);
const grantForm = reactive({
  resourceCode: '',
  actionCode: '',
  subjectType: 'ROLE' as string,
  subjectId: '',
  effect: 'ALLOW' as string,
  description: '',
});
const grantRows = ref<SystemAuthorizationApi.AuthorizationGrant[]>([]);

const grantColumns: TableProps['columns'] = [
  { title: '资源', dataIndex: 'resourceCode', key: 'resourceCode', width: 200 },
  { title: '动作', dataIndex: 'actionCode', key: 'actionCode', width: 120 },
  { title: '主体类型', dataIndex: 'subjectType', key: 'subjectType', width: 110 },
  { title: '主体ID', dataIndex: 'subjectId', key: 'subjectId', width: 160 },
  { title: '效果', dataIndex: 'effect', key: 'effect', width: 100 },
  { title: '操作', key: 'action', width: 120 },
];

const subjectTypeOptions = [
  { label: '角色 (ROLE)', value: 'ROLE' },
  { label: '用户 (USER)', value: 'USER' },
];

function asStringValue(value: unknown) {
  return typeof value === 'string' ? value : String(value ?? '');
}

function changeGrantResource(value: unknown) {
  grantForm.resourceCode = asStringValue(value);
  grantForm.actionCode = '';
}

function openGrantDrawer() {
  grantForm.resourceCode = ctx.resourceOptions.value[0]?.value ?? '';
  grantForm.actionCode = '';
  grantForm.subjectType = 'ROLE';
  grantForm.subjectId = ctx.selectedRoleId.value || '';
  grantForm.effect = 'ALLOW';
  grantForm.description = '';
  grantDrawerOpen.value = true;
}

async function handleSaveGrant() {
  if (!grantForm.resourceCode || !grantForm.actionCode || !grantForm.subjectId) {
    message.warning('请填写完整信息');
    return;
  }
  ctx.saving.value = true;
  try {
    await saveAuthorizationGrant({
      resourceCode: grantForm.resourceCode,
      actionCode: grantForm.actionCode,
      subjectType: grantForm.subjectType as 'ROLE' | 'USER',
      subjectId: grantForm.subjectId,
      effect: grantForm.effect as 'ALLOW' | 'DENY',
    });
    message.success('授权已保存');
    grantDrawerOpen.value = false;
    await loadGrants();
  } catch {
    message.error('保存授权失败');
  } finally {
    ctx.saving.value = false;
  }
}

async function handleDeleteGrant(record: SystemAuthorizationApi.AuthorizationGrant) {
  if (!record.id) return;
  try {
    await deleteAuthorizationGrant(record.id);
    message.success('授权已删除');
    await loadGrants();
  } catch {
    message.error('删除授权失败');
  }
}

async function loadGrants() {
  if (!ctx.canQuery.value || !ctx.selectedRoleId.value) {
    grantRows.value = [];
    return;
  }
  try {
    const profile = await getRoleAuthorizationProfile(ctx.selectedRoleId.value);
    grantRows.value = profile.functionGrants ?? [];
  } catch {
    grantRows.value = [];
  }
}

function effectTagColor(effect?: string) {
  return effect === 'DENY' ? 'error' : 'success';
}

watch(() => ctx.selectedRoleId.value, () => {
  grantRows.value = [];
});
</script>

<template>
  <div>
    <div class="mb-3 flex items-center justify-between">
      <span class="text-muted-foreground text-sm">按资源 + 动作授权给角色或用户</span>
      <Space v-if="ctx.canCreate.value">
        <Tooltip title="刷新">
          <Button shape="circle" @click="loadGrants">
            <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
          </Button>
        </Tooltip>
        <Button type="primary" @click="openGrantDrawer">
          <IconifyIcon icon="lucide:plus" class="mr-1" />新增授权
        </Button>
      </Space>
    </div>
    <ClientPaginatedTable
        :columns="grantColumns"
        :data-source="grantRows"
        :loading="ctx.loading.value"
        row-key="id"
      >
        <template #bodyCell="{ column, record }: any">
          <template v-if="column.key === 'effect'">
            <Tag :color="effectTagColor(record.effect)">{{ record.effect }}</Tag>
          </template>
          <template v-else-if="column.key === 'action' && ctx.canDelete.value">
            <Popconfirm title="确认删除此授权?" @confirm="handleDeleteGrant(record)">
              <Button type="link" danger size="small" @click.stop>删除</Button>
            </Popconfirm>
          </template>
        </template>
    </ClientPaginatedTable>

    <Drawer v-model:open="grantDrawerOpen" title="新增授权" :width="500">
      <Form layout="vertical">
        <FormItem label="资源">
          <Select v-model:value="grantForm.resourceCode" :options="ctx.resourceOptions.value" @change="changeGrantResource" />
        </FormItem>
        <FormItem label="动作">
          <Select v-model:value="grantForm.actionCode" :options="ctx.actionOptionsForResource(grantForm.resourceCode)" />
        </FormItem>
        <FormItem label="主体类型">
          <Select v-model:value="grantForm.subjectType" :options="subjectTypeOptions" />
        </FormItem>
        <FormItem label="主体ID">
          <Input v-model:value="grantForm.subjectId" placeholder="输入角色编码或用户ID" />
        </FormItem>
        <FormItem label="效果">
          <Select
            v-model:value="grantForm.effect"
            :options="[
              { label: 'ALLOW', value: 'ALLOW' },
              { label: 'DENY', value: 'DENY' },
            ]"
          />
        </FormItem>
        <FormItem label="描述">
          <Input.TextArea v-model:value="grantForm.description" :rows="2" />
        </FormItem>
      </Form>
      <template #footer>
        <Button @click="grantDrawerOpen = false">取消</Button>
        <Button type="primary" :loading="ctx.saving.value" @click="handleSaveGrant">保存</Button>
      </template>
    </Drawer>
  </div>
</template>
