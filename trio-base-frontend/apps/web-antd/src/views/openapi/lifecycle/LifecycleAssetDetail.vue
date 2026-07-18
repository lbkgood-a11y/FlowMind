<script setup lang="ts">
import type { OpenApiOperationsApi } from '#/api';
import type { LifecycleResourceConfig } from './resource-config';
import type { PayloadFormDefinition, PayloadFormField } from './payload-form';

import { computed } from 'vue';

import { Collapse, Descriptions, Tag } from 'ant-design-vue';

import StructuredValue from './StructuredValue.vue';

const props = defineProps<{
  asset?: OpenApiOperationsApi.LifecycleAsset;
  config: LifecycleResourceConfig;
}>();

const CollapsePanel = Collapse.Panel;

const commonLabels: Record<string, string> = {
  assetKey: '标识',
  assetType: '资产类型',
  createdAt: '创建时间',
  displayName: '名称',
  id: 'ID',
  lifecycleState: '生命周期状态',
  parentReferenceCode: '所属资产',
  referenceCode: '引用标签',
  tenantId: '租户',
  updatedAt: '更新时间',
  versionNumber: '版本号',
};

const labelMap = computed(() => ({
  ...commonLabels,
  ...formLabels(props.config.createForm),
  ...(props.config.edit ? formLabels(props.config.edit.form) : {}),
}));

const summaryItems = computed<Array<[string, unknown]>>(() => {
  const asset = props.asset;
  if (!asset) return [];
  return [
    ['assetKey', asset.assetKey],
    ['displayName', asset.displayName],
    ['lifecycleState', asset.lifecycleState],
    ['tenantId', asset.tenantId],
    ['id', asset.id],
    ['createdAt', asset.createdAt],
    ['updatedAt', asset.updatedAt],
  ].filter(([, value]) => value !== undefined && value !== null && value !== '') as Array<[string, unknown]>;
});

const structuredDetail = computed(() => {
  const detail = props.asset?.detail ?? {};
  const hidden = new Set([
    'assetKey',
    'assetType',
    'createdAt',
    'displayName',
    'id',
    'lifecycleState',
    'tenantId',
    'updatedAt',
  ]);
  return Object.fromEntries(Object.entries(detail).filter(([key]) => !hidden.has(key)));
});

function formLabels(form: PayloadFormDefinition) {
  return Object.fromEntries(flattenFields(form.groups.flatMap((group) => group.fields))
    .map((field) => [field.key, field.label]));
}

function flattenFields(fields: PayloadFormField[]): PayloadFormField[] {
  return fields.flatMap((field) => [
    field,
    ...flattenFields([...(field.fields ?? []), ...(field.itemFields ?? [])]),
  ]);
}
</script>

<template>
  <div v-if="asset" class="asset-detail">
    <Descriptions bordered size="small" :column="2">
      <Descriptions.Item v-for="[key, value] in summaryItems" :key="key" :label="labelMap[key] ?? key">
        <Tag v-if="key === 'lifecycleState'" color="blue">{{ value }}</Tag>
        <span v-else>{{ value }}</span>
      </Descriptions.Item>
    </Descriptions>

    <section class="detail-section">
      <div class="detail-section-title">配置明细</div>
      <StructuredValue :label-map="labelMap" :value="structuredDetail" />
    </section>

    <Collapse class="raw-collapse" ghost>
      <CollapsePanel key="raw" header="高级原始字段">
        <pre>{{ JSON.stringify(asset.detail, null, 2) }}</pre>
      </CollapsePanel>
    </Collapse>
  </div>
</template>

<style scoped>
.asset-detail {
  display: grid;
  gap: 16px;
}

.detail-section-title {
  margin-bottom: 8px;
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
}

.raw-collapse {
  border-top: 1px solid #edf2f7;
}

pre {
  max-height: 360px;
  padding: 12px;
  overflow: auto;
  color: #e2e8f0;
  background: #0f172a;
  border-radius: 6px;
}
</style>
