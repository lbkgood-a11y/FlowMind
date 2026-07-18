<script setup lang="ts">
import type { OpenApiOperationsApi } from '#/api';
import type { PayloadFormReference } from './payload-form';

import { computed, onMounted, ref, watch } from 'vue';

import { Select } from 'ant-design-vue';

import { getLifecycleAssets } from '#/api';

const props = withDefaults(
  defineProps<{
    modelValue?: string | string[];
    multiple?: boolean;
    placeholder?: string;
    reference: PayloadFormReference;
  }>(),
  {
    modelValue: undefined,
    multiple: false,
    placeholder: '搜索标识、名称或编码',
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string | string[]];
}>();

const assets = ref<OpenApiOperationsApi.LifecycleAsset[]>([]);
const keyword = ref('');
const loading = ref(false);
const requestSeq = ref(0);

const options = computed(() => {
  const loaded = assets.value
    .map((asset) => {
      const value = optionValue(asset);
      return value
        ? {
            label: optionLabel(asset),
            value,
          }
        : undefined;
    })
    .filter(Boolean) as Array<{ label: string; value: string }>;
  const loadedValues = new Set(loaded.map((item) => item.value));
  const selectedFallbacks = selectedValues.value
    .filter((value) => !loadedValues.has(value))
    .map((value) => ({ label: `已选 ${value}`, value }));
  return [...loaded, ...selectedFallbacks];
});

const selectedValues = computed(() => {
  if (Array.isArray(props.modelValue)) {
    return props.modelValue.filter(Boolean);
  }
  return props.modelValue ? [props.modelValue] : [];
});

function firstPath(
  asset: OpenApiOperationsApi.LifecycleAsset,
  paths: Array<string | undefined>,
) {
  for (const path of paths) {
    if (!path) continue;
    const value = readPath(asset, path);
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return String(value);
    }
  }
  return undefined;
}

function handleDropdownVisibleChange(open: boolean) {
  if (open && assets.value.length === 0) {
    void loadOptions(keyword.value);
  }
}

function handleSearch(value: string) {
  keyword.value = value;
  void loadOptions(value);
}

function optionLabel(asset: OpenApiOperationsApi.LifecycleAsset) {
  const code = firstPath(asset, [
    props.reference.codePath,
    'detail.referenceCode',
    'assetKey',
    'detail.clientKey',
    'detail.semanticVersion',
    'detail.versionNumber',
  ]);
  const name = firstPath(asset, [props.reference.labelPath, 'displayName']);
  const state = asset.lifecycleState ? `状态 ${asset.lifecycleState}` : undefined;
  const idHint = asset.id && asset.id !== code ? `ID ${asset.id.slice(0, 8)}` : undefined;
  return [code, name !== code ? name : undefined, state, idHint].filter(Boolean).join(' · ');
}

function optionValue(asset: OpenApiOperationsApi.LifecycleAsset) {
  const value = readPath(asset, props.reference.valuePath ?? 'id');
  return value === undefined || value === null ? undefined : String(value);
}

function readPath(value: Record<string, any>, path: string) {
  return path.split('.').reduce<unknown>((current, key) => {
    if (current && typeof current === 'object' && key in current) {
      return (current as Record<string, any>)[key];
    }
    return undefined;
  }, value);
}

async function loadOptions(nextKeyword = '') {
  const seq = requestSeq.value + 1;
  requestSeq.value = seq;
  loading.value = true;
  try {
    const result = await getLifecycleAssets(props.reference.assetType, {
      keyword: nextKeyword || undefined,
      page: 1,
      size: 50,
      state: props.reference.state,
    });
    if (requestSeq.value === seq) {
      assets.value = result.records;
    }
  } finally {
    if (requestSeq.value === seq) {
      loading.value = false;
    }
  }
}

function updateValue(value: unknown) {
  if (props.multiple) {
    emit(
      'update:modelValue',
      Array.isArray(value) ? value.map((item) => String(item)) : [],
    );
    return;
  }
  emit('update:modelValue', value === undefined || value === null ? '' : String(value));
}

watch(
  () => props.reference.assetType,
  () => {
    assets.value = [];
    keyword.value = '';
    void loadOptions();
  },
);

onMounted(() => loadOptions());
</script>

<template>
  <Select
    allow-clear
    class="asset-reference-select"
    :filter-option="false"
    :loading="loading"
    :mode="multiple ? 'multiple' : undefined"
    :not-found-content="loading ? '加载中' : '暂无可选资产'"
    :options="options"
    :placeholder="placeholder"
    show-search
    :value="modelValue"
    @dropdown-visible-change="handleDropdownVisibleChange"
    @search="handleSearch"
    @update:value="updateValue"
  />
</template>

<style scoped>
.asset-reference-select {
  width: 100%;
}
</style>
