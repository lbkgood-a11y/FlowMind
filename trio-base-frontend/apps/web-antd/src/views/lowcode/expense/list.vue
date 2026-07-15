<script setup lang="ts">
import type { LowcodeApi } from '#/api/lowcode';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  Drawer,
  message,
  Pagination,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  bindFormInstanceProcess,
  getFormDefinitionList,
  getFormInstanceList,
  submitFormInstance,
} from '#/api/lowcode';
import { startProcessInstance } from '#/api/process';

import DynamicProcessForm from '../../process/components/DynamicProcessForm.vue';

const FORM_KEY = 'expense';
const PERMISSIONS = {
  create: '/api/v1/forms/expense/submit:POST',
  query: '/api/v1/forms/expense/instances:GET',
  start: '/api/v1/process-instances/start:POST',
} as const;

const { hasAccessByCodes } = useAccess();
const canCreate = computed(() => hasAccessByCodes([PERMISSIONS.create]));
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canStart = computed(() => hasAccessByCodes([PERMISSIONS.start]));

const definition = ref<LowcodeApi.FormDefinition>();
const records = ref<LowcodeApi.FormInstance[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawerOpen = ref(false);
const formRef = ref<InstanceType<typeof DynamicProcessForm>>();
const formData = ref<Record<string, unknown>>({});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const columns: TableProps['columns'] = [
  { dataIndex: 'amount', key: 'amount', title: '报销金额', width: 130 },
  { dataIndex: 'reason', ellipsis: true, key: 'reason', title: '报销事由', width: 300 },
  { dataIndex: 'submittedBy', key: 'submittedBy', title: '提交人', width: 150 },
  { dataIndex: 'submittedAt', key: 'submittedAt', title: '提交时间', width: 180 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 110 },
  { fixed: 'right', key: 'action', title: '操作', width: 120 },
];

const tableRecords = computed(() =>
  records.value.map((record) => {
    let data: Record<string, unknown> = {};
    try {
      data = JSON.parse(record.dataJson) as Record<string, unknown>;
    } catch {
      data = {};
    }
    return { ...record, ...data };
  }),
);

async function loadDefinition() {
  const result = await getFormDefinitionList({ page: 1, size: 100 });
  definition.value = result.items.find(
    (item) => item.formKey === FORM_KEY && item.status === 'PUBLISHED',
  );
}

async function loadRecords(page = pagination.current) {
  if (!canQuery.value) {
    records.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getFormInstanceList(FORM_KEY, {
      page,
      size: pagination.pageSize,
    });
    records.value = result.items;
    pagination.current = page;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  if (!definition.value) {
    message.warning('费用报销表单尚未发布');
    return;
  }
  formData.value = {};
  drawerOpen.value = true;
}

async function submit() {
  const errors = formRef.value?.validate() ?? [];
  if (errors.length > 0) {
    message.warning(errors[0]?.message ?? '请完善表单');
    return;
  }
  saving.value = true;
  let instanceCreated = false;
  try {
    const instance = await submitFormInstance(FORM_KEY, formData.value);
    instanceCreated = true;
    await startWorkflow(instance, formData.value);
    message.success('费用报销已提交并进入审批流程');
    drawerOpen.value = false;
    await loadRecords(1);
  } catch {
    if (instanceCreated) {
      message.error('费用单据已保存，但流程启动失败，请在列表中点击“启动流程”重试');
      drawerOpen.value = false;
      await loadRecords(1);
    } else {
      message.error('费用报销提交失败');
    }
  } finally {
    saving.value = false;
  }
}

async function startWorkflow(
  instance: LowcodeApi.FormInstance,
  data: Record<string, unknown>,
) {
  if (instance.processInstanceId) {
    return;
  }
  const process = await startProcessInstance({
    businessId: instance.id,
    businessType: 'expense_report',
    formData: {
      ...data,
      businessId: instance.id,
      formInstanceId: instance.id,
    },
    idempotencyKey: `expense-form:${instance.id}`,
    launchMode: 'EXISTING_DOCUMENT',
    processKey: 'expense_report',
    title: `费用报销-${String(data.reason ?? instance.id)}`,
  });
  await bindFormInstanceProcess(FORM_KEY, instance.id, {
    processInstanceId: process.id,
    processKey: process.processKey,
    workflowStatus: process.status,
  });
}

async function retryStart(record: Record<string, unknown>) {
  const instance = record as unknown as LowcodeApi.FormInstance;
  const data = {
    amount: record.amount,
    reason: record.reason,
  };
  await startWorkflow(instance, data);
  message.success('流程已启动');
  await loadRecords();
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRecords(page);
}

onMounted(async () => {
  await Promise.all([loadDefinition(), loadRecords(1)]);
});
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page expense-page">
      <section class="list-panel">
        <div class="list-header">
          <div>
            <h2>费用报销</h2>
            <p>当前列表已按 FORM:EXPENSE 数据权限策略过滤。</p>
          </div>
          <Button v-if="canCreate" type="primary" @click="openCreate">
            <Plus class="size-4" />
            发起报销
          </Button>
        </div>

        <div class="table-frame">
          <Table
            row-key="id"
            :columns="columns"
            :data-source="tableRecords"
            :loading="loading"
            :pagination="false"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'amount'">
                ¥ {{ Number(record.amount || 0).toFixed(2) }}
              </template>
              <template v-else-if="column.key === 'status'">
                <Tag :color="record.processInstanceId ? 'green' : 'orange'">
                  {{ record.processInstanceId ? '审批中' : '待启动流程' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Button
                  v-if="canStart && !record.processInstanceId"
                  size="small"
                  type="link"
                  @click="retryStart(record)"
                >
                  启动流程
                </Button>
                <span v-else>{{ record.processInstanceId || '-' }}</span>
              </template>
            </template>
          </Table>
        </div>

        <Pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          show-size-changer
          :show-total="(total: number) => `共 ${total} 条`"
          size="small"
          @change="onPageChange"
        />
      </section>
    </div>

    <Drawer v-model:open="drawerOpen" title="发起费用报销" width="640">
      <DynamicProcessForm
        ref="formRef"
        v-model="formData"
        :schema-json="definition?.schemaJson"
        :ui-schema-json="definition?.uiSchemaJson"
      />

      <template #footer>
        <Space>
          <Button @click="drawerOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submit">提交报销</Button>
        </Space>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.expense-page,
.list-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.list-panel {
  padding: 8px;
  background: #fff;
}

.list-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
}

.list-header h2 {
  margin: 0;
  font-size: 14px;
  color: #111827;
}

.list-header p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #64748b;
}

.table-frame {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
