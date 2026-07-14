<script setup lang="ts">
import type { DataApi } from '#/api/data';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Button,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Select,
  Space,
  Table,
  Tabs,
  Textarea,
} from 'ant-design-vue';

import {
  createDataset,
  getDatasetList,
  ingestDocument,
  runHybridQuery,
} from '#/api/data';

const PERMISSIONS = {
  createDataset: '/api/v1/data/datasets:POST',
  ingest: '/api/v1/data/documents:POST',
  query: '/api/v1/data/query/hybrid:POST',
  queryDatasets: '/api/v1/data/datasets:GET',
} as const;

const { hasAccessByCodes } = useAccess();
const canCreateDataset = computed(() => hasAccessByCodes([PERMISSIONS.createDataset]));
const canIngest = computed(() => hasAccessByCodes([PERMISSIONS.ingest]));
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canQueryDatasets = computed(() => hasAccessByCodes([PERMISSIONS.queryDatasets]));

const datasets = ref<DataApi.Dataset[]>([]);
const loadingDatasets = ref(false);
const savingDataset = ref(false);
const ingesting = ref(false);
const querying = ref(false);
const queryResult = ref<DataApi.HybridQueryResponse>();

const datasetForm = reactive({
  backingTable: 'data_sample_expense',
  datasetKey: `sample_${Date.now()}`,
  description: '',
  name: 'Sample Dataset',
});

const documentForm = reactive({
  collectionKey: 'expense_docs',
  content: 'Expense approval policy: amounts above 5000 require finance review. Team workshop materials are usually approved by department heads.',
  sourceKey: 'expense_policy',
  title: 'Expense Policy',
});

const queryForm = reactive({
  collectionKey: 'expense_docs',
  datasetKey: 'expense_report_sample',
  department: '',
  mode: 'HYBRID',
  query: 'Which expense needs finance review?',
  status: '',
  topK: 5,
});

const datasetOptions = computed(() =>
  datasets.value.map((item) => ({
    label: `${item.name} (${item.datasetKey})`,
    value: item.datasetKey,
  })),
);

const datasetColumns: TableProps['columns'] = [
  { dataIndex: 'name', key: 'name', title: '名称', width: 180 },
  { dataIndex: 'datasetKey', key: 'datasetKey', title: '标识', width: 180 },
  { dataIndex: 'datasetType', key: 'datasetType', title: '类型', width: 110 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 100 },
  { dataIndex: 'backingTable', key: 'backingTable', title: '数据表', width: 180 },
];

const rowColumns = computed<TableProps['columns']>(() => {
  const rows = queryResult.value?.structured?.rows ?? [];
  const first = rows[0] ?? {};
  return Object.keys(first).map((key) => ({
    dataIndex: key,
    key,
    title: key,
    width: 140,
  }));
});

async function loadDatasets() {
  if (!canQueryDatasets.value) {
    datasets.value = [];
    return;
  }
  loadingDatasets.value = true;
  try {
    const result = await getDatasetList({ page: 1, size: 50, status: 'ACTIVE' });
    datasets.value = result.items;
  } finally {
    loadingDatasets.value = false;
  }
}

async function handleCreateDataset() {
  savingDataset.value = true;
  try {
    await createDataset({
      backingTable: datasetForm.backingTable,
      datasetKey: datasetForm.datasetKey.trim(),
      datasetType: 'STRUCTURED',
      description: datasetForm.description || undefined,
      fields: [
        { fieldKey: 'applicant', fieldType: 'STRING', label: 'Applicant', searchable: true, sortable: true, sortOrder: 10 },
        { fieldKey: 'department', fieldType: 'STRING', label: 'Department', searchable: true, sortable: true, sortOrder: 20 },
        { fieldKey: 'amount', fieldType: 'NUMBER', label: 'Amount', sortable: true, sortOrder: 30 },
        { fieldKey: 'status', fieldType: 'STRING', label: 'Status', searchable: true, sortable: true, sortOrder: 40 },
      ],
      name: datasetForm.name.trim(),
    });
    message.success('数据集已创建');
    await loadDatasets();
  } finally {
    savingDataset.value = false;
  }
}

async function handleIngestDocument() {
  ingesting.value = true;
  try {
    const result = await ingestDocument({
      collectionKey: documentForm.collectionKey.trim(),
      content: documentForm.content,
      sourceKey: documentForm.sourceKey || undefined,
      title: documentForm.title.trim(),
    });
    message.success(`文档已入库，${result.chunkCount} 个分片`);
  } finally {
    ingesting.value = false;
  }
}

async function handleQuery() {
  querying.value = true;
  try {
    const filters: Record<string, string> = {};
    if (queryForm.department) filters.department = queryForm.department;
    if (queryForm.status) filters.status = queryForm.status;
    queryResult.value = await runHybridQuery({
      mode: queryForm.mode,
      semantic: {
        collectionKey: queryForm.collectionKey,
        query: queryForm.query,
        topK: queryForm.topK,
      },
      structured: {
        datasetKey: queryForm.datasetKey,
        filters,
        page: 1,
        size: 20,
      },
    });
  } finally {
    querying.value = false;
  }
}

onMounted(loadDatasets);
</script>

<template>
  <Page auto-content-height>
    <div class="data-page">
      <section class="panel">
        <div class="panel-header">
          <h2>数据接入与混合查询</h2>
          <Button :loading="loadingDatasets" @click="loadDatasets">刷新</Button>
        </div>

        <Tabs>
          <Tabs.TabPane key="catalog" tab="数据目录">
            <div class="grid">
              <Form class="form-panel" layout="vertical">
                <FormItem label="数据集标识">
                  <Input v-model:value="datasetForm.datasetKey" />
                </FormItem>
                <FormItem label="名称">
                  <Input v-model:value="datasetForm.name" />
                </FormItem>
                <FormItem label="数据表">
                  <Input v-model:value="datasetForm.backingTable" />
                </FormItem>
                <FormItem label="说明">
                  <Textarea v-model:value="datasetForm.description" :rows="3" />
                </FormItem>
                <Button
                  :disabled="!canCreateDataset"
                  :loading="savingDataset"
                  type="primary"
                  @click="handleCreateDataset"
                >
                  创建数据集
                </Button>
              </Form>

              <Table
                :columns="datasetColumns"
                :data-source="datasets"
                :loading="loadingDatasets"
                :pagination="false"
                bordered
                row-key="id"
                size="small"
              />
            </div>
          </Tabs.TabPane>

          <Tabs.TabPane key="query" tab="混合查询">
            <div class="grid">
              <Form class="form-panel" layout="vertical">
                <FormItem label="查询模式">
                  <Select
                    v-model:value="queryForm.mode"
                    :options="[
                      { label: 'HYBRID', value: 'HYBRID' },
                      { label: 'STRUCTURED', value: 'STRUCTURED' },
                      { label: 'SEMANTIC', value: 'SEMANTIC' },
                    ]"
                  />
                </FormItem>
                <FormItem label="数据集">
                  <Select
                    v-model:value="queryForm.datasetKey"
                    show-search
                    :options="datasetOptions"
                  />
                </FormItem>
                <FormItem label="部门过滤">
                  <Input v-model:value="queryForm.department" allow-clear />
                </FormItem>
                <FormItem label="状态过滤">
                  <Input v-model:value="queryForm.status" allow-clear />
                </FormItem>
                <FormItem label="文档集合">
                  <Input v-model:value="queryForm.collectionKey" />
                </FormItem>
                <FormItem label="语义问题">
                  <Textarea v-model:value="queryForm.query" :rows="3" />
                </FormItem>
                <FormItem label="Top K">
                  <InputNumber v-model:value="queryForm.topK" :min="1" :max="20" />
                </FormItem>
                <Space>
                  <Button
                    :disabled="!canIngest"
                    :loading="ingesting"
                    @click="handleIngestDocument"
                  >
                    入库样例文档
                  </Button>
                  <Button
                    :disabled="!canQuery"
                    :loading="querying"
                    type="primary"
                    @click="handleQuery"
                  >
                    运行查询
                  </Button>
                </Space>
              </Form>

              <div class="result-panel">
                <h3>结构化结果</h3>
                <Table
                  :columns="rowColumns"
                  :data-source="queryResult?.structured?.rows ?? []"
                  :pagination="false"
                  bordered
                  row-key="id"
                  size="small"
                />

                <h3>语义结果</h3>
                <div
                  v-for="chunk in queryResult?.semantic?.chunks ?? []"
                  :key="`${chunk.documentId}-${chunk.chunkIndex}`"
                  class="chunk"
                >
                  <div class="chunk-title">{{ chunk.title }} · #{{ chunk.chunkIndex }} · {{ chunk.score.toFixed(3) }}</div>
                  <p>{{ chunk.content }}</p>
                </div>
              </div>
            </div>
          </Tabs.TabPane>
        </Tabs>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.data-page {
  padding: 8px;
}

.panel {
  min-height: calc(100vh - 120px);
  padding: 10px;
  background: #fff;
  border-radius: 4px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  margin-bottom: 8px;
}

.panel-header h2,
.result-panel h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.grid {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 12px;
}

.form-panel,
.result-panel {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.result-panel {
  min-width: 0;
}

.result-panel h3 {
  margin: 4px 0 10px;
}

.chunk {
  margin-bottom: 8px;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.chunk-title {
  margin-bottom: 4px;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}

.chunk p {
  margin: 0;
  color: #111827;
}

@media (max-width: 960px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
