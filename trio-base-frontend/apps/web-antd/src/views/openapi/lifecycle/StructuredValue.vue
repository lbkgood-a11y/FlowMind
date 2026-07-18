<script setup lang="ts">
import { computed } from 'vue';

import { Space, Tag } from 'ant-design-vue';

defineOptions({ name: 'StructuredValue' });

const props = defineProps<{
  labelMap?: Record<string, string>;
  value: unknown;
}>();

const isArray = computed(() => Array.isArray(props.value));
const isObject = computed(
  () => typeof props.value === 'object' && props.value !== null && !Array.isArray(props.value),
);
const primitiveArray = computed(
  () => isArray.value && (props.value as unknown[]).every((item) => !isStructured(item)),
);

function entries(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return [];
  }
  return Object.entries(value as Record<string, unknown>).filter(([, item]) => !isEmpty(item));
}

function formatPrimitive(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }
  return String(value);
}

function isEmpty(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return true;
  }
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  return typeof value === 'object' && Object.keys(value as Record<string, unknown>).length === 0;
}

function isStructured(value: unknown) {
  return typeof value === 'object' && value !== null;
}

function labelFor(key: string) {
  return props.labelMap?.[key] ?? key;
}
</script>

<template>
  <Tag v-if="typeof value === 'boolean'" :color="value ? 'green' : 'default'">
    {{ formatPrimitive(value) }}
  </Tag>
  <span v-else-if="!isArray && !isObject">{{ formatPrimitive(value) }}</span>
  <span v-else-if="isArray && (value as unknown[]).length === 0">-</span>
  <Space v-else-if="primitiveArray" wrap>
    <Tag v-for="item in value as unknown[]" :key="String(item)">
      {{ formatPrimitive(item) }}
    </Tag>
  </Space>
  <div v-else-if="isArray" class="structured-list">
    <div v-for="(item, index) in value as unknown[]" :key="index" class="structured-list-item">
      <div class="structured-list-title">第 {{ index + 1 }} 项</div>
      <StructuredValue :label-map="labelMap" :value="item" />
    </div>
  </div>
  <div v-else class="structured-map">
    <div v-for="[key, item] in entries(value)" :key="key" class="structured-row">
      <div class="structured-key">{{ labelFor(key) }}</div>
      <div class="structured-value">
        <StructuredValue :label-map="labelMap" :value="item" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.structured-list {
  display: grid;
  gap: 8px;
}

.structured-list-item {
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.structured-list-title {
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

.structured-map {
  display: grid;
  gap: 6px;
}

.structured-row {
  display: grid;
  grid-template-columns: minmax(110px, 180px) minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding-bottom: 6px;
  border-bottom: 1px dashed #edf2f7;
}

.structured-row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.structured-key {
  color: #64748b;
  font-size: 12px;
}

.structured-value {
  min-width: 0;
  color: #111827;
  word-break: break-word;
}

@media (max-width: 720px) {
  .structured-row {
    grid-template-columns: 1fr;
  }
}
</style>
