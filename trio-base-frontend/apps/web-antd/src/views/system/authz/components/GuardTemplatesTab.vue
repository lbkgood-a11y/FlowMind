<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api';

import { inject, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Drawer,
  Form,
  FormItem,
  Input,
  message,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  saveAuthorizationGuardTemplate,
  updateAuthorizationGuardTemplateStatus,
} from '#/api';
import { CompactTableFrame } from '#/shared';

const ctx = inject<any>('authzContext')!;

const guardDrawerOpen = ref(false);
const guardForm = reactive({
  guardCode: '',
  ownerService: '',
  supportedResourceTypes: '',
  description: '',
  configSchemaJson: '',
  status: 1 as number,
});
const guardRows = ref<SystemAuthorizationApi.GuardTemplate[]>([]);

const guardColumns: TableProps['columns'] = [
  { title: '守卫代码', dataIndex: 'guardCode', key: 'guardCode', width: 180 },
  { title: '所属服务', dataIndex: 'ownerService', key: 'ownerService', width: 180 },
  { title: '支持资源类型', dataIndex: 'supportedResourceTypes', key: 'supportedResourceTypes', width: 200 },
  { title: '描述', dataIndex: 'description', key: 'description', width: 240, ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 180 },
];

const clientPagination: TableProps['pagination'] = {
  pageSize: 20,
  showQuickJumper: true,
  showSizeChanger: true,
  showTotal: (total, range) => `共 ${total} 条记录，本页 ${range[0]}-${range[1]} 条`,
};

function openGuardDrawer() {
  guardForm.guardCode = '';
  guardForm.ownerService = '';
  guardForm.supportedResourceTypes = '';
  guardForm.description = '';
  guardForm.configSchemaJson = '';
  guardForm.status = 1;
  guardDrawerOpen.value = true;
}

async function handleSaveGuard() {
  if (!guardForm.guardCode || !guardForm.ownerService) {
    message.warning('请填写守卫代码和所属服务');
    return;
  }
  ctx.saving.value = true;
  try {
    await saveAuthorizationGuardTemplate({
      guardCode: guardForm.guardCode,
      ownerService: guardForm.ownerService,
      supportedResourceTypes: guardForm.supportedResourceTypes || undefined,
      description: guardForm.description || undefined,
      configSchemaJson: guardForm.configSchemaJson || undefined,
    });
    message.success('守卫模板已保存');
    guardDrawerOpen.value = false;
    await loadGuards();
  } catch {
    message.error('保存守卫模板失败');
  } finally {
    ctx.saving.value = false;
  }
}

async function handleToggleGuard(
  record: SystemAuthorizationApi.GuardTemplate,
  checked: boolean | number | string,
) {
  if (!record.id) return;
  const enabled = checked === true || checked === 1 || checked === '1';
  ctx.saving.value = true;
  try {
    await updateAuthorizationGuardTemplateStatus(record.id, enabled ? 1 : 0);
    message.success(enabled ? '已启用' : '已禁用');
    await loadGuards();
  } catch {
    message.error('更新状态失败');
  } finally {
    ctx.saving.value = false;
  }
}

async function handleResetGuard(record: SystemAuthorizationApi.GuardTemplate) {
  if (!record.id) return;
  try {
    await updateAuthorizationGuardTemplateStatus(record.id, 1);
    message.success('守卫模板已重置为启用');
    await loadGuards();
  } catch {
    message.error('重置失败');
  }
}

async function loadGuards() {
  guardRows.value = ctx.adminOptions.value?.guardTemplates ?? [];
}

function statusTagColor(status?: number) {
  return status === 1 ? 'success' : 'default';
}
</script>

<template>
  <div>
    <div class="mb-3 flex items-center justify-between">
      <span class="text-muted-foreground text-sm">管理运行时守卫模板的启用/禁用</span>
      <Space v-if="ctx.canCreate.value">
        <Button class="!h-8 !w-8" @click="loadGuards">
          <IconifyIcon icon="lucide:refresh-cw" />
        </Button>
        <Button type="primary" @click="openGuardDrawer">
          <IconifyIcon icon="lucide:plus" class="mr-1" />新增守卫
        </Button>
      </Space>
    </div>
    <CompactTableFrame>
      <Table
        :columns="guardColumns"
        :data-source="guardRows"
        :loading="ctx.loading.value"
        :pagination="clientPagination"
        row-key="id"
        size="small"
        bordered
      >
        <template #bodyCell="{ column, record }: any">
          <template v-if="column.key === 'status'">
            <Tag :color="statusTagColor(record.status)">{{ record.status === 1 ? '启用' : '禁用' }}</Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Switch
                v-if="ctx.canUpdate.value"
                :checked="record.status === 1"
                checked-children="启用"
                un-checked-children="禁用"
                :loading="ctx.saving.value"
                @change="(checked: any) => handleToggleGuard(record, checked)"
              />
              <Popconfirm
                v-if="ctx.canDelete.value"
                title="确认重置此守卫?"
                @confirm="handleResetGuard(record)"
              >
                <Button type="link" size="small" @click.stop>重置</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </CompactTableFrame>

    <Drawer v-model:open="guardDrawerOpen" title="新增守卫模板" :width="500">
      <Form layout="vertical">
        <FormItem label="守卫代码" required>
          <Input v-model:value="guardForm.guardCode" placeholder="如 NO_SELF_APPROVAL" />
        </FormItem>
        <FormItem label="所属服务" required>
          <Input v-model:value="guardForm.ownerService" placeholder="如 service-workflow-engine" />
        </FormItem>
        <FormItem label="支持资源类型">
          <Input v-model:value="guardForm.supportedResourceTypes" placeholder="如 LOWCODE_FORM,WORKFLOW_TASK" />
        </FormItem>
        <FormItem label="描述">
          <Input.TextArea v-model:value="guardForm.description" :rows="2" />
        </FormItem>
        <FormItem label="配置 JSON Schema">
          <Input.TextArea v-model:value="guardForm.configSchemaJson" :rows="3" placeholder="选填" />
        </FormItem>
      </Form>
      <template #footer>
        <Button @click="guardDrawerOpen = false">取消</Button>
        <Button type="primary" :loading="ctx.saving.value" @click="handleSaveGuard">保存</Button>
      </template>
    </Drawer>
  </div>
</template>
