<script lang="ts" setup>
import type { SelectProps } from 'ant-design-vue';

import type { PropType, VNodeChild } from 'vue';

import { computed, defineComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import { Pagination, Select, Spin } from 'ant-design-vue';

type PagedSelectRawValue = number | string;
type PagedSelectValue = SelectProps['value'];
type PagedSelectMode = NonNullable<SelectProps['mode']>;
type PagedSelectRawOption = Record<string, any>;

export type PagedSelectRequestParams = Record<string, any> & {
  keyword?: string;
  page?: number;
  size?: number;
};

export type PagedSelectApiResult =
  | PagedSelectRawOption[]
  | Record<string, any>;

export type PagedSelectOption = PagedSelectRawOption & {
  disabled?: boolean;
  label: unknown;
  raw: PagedSelectRawOption;
  value: PagedSelectRawValue;
};

defineOptions({
  name: 'PagedSelect',
  inheritAttrs: false,
});

const props = withDefaults(
  defineProps<{
    allowClear?: boolean;
    alwaysLoad?: boolean;
    alwaysShowPagination?: boolean;
    api?: (params: PagedSelectRequestParams) => Promise<PagedSelectApiResult>;
    disabled?: boolean;
    disabledField?: string;
    fetchSelectedApi?: (values: PagedSelectRawValue[]) => Promise<PagedSelectApiResult>;
    immediate?: boolean;
    keywordParam?: string;
    labelField?: string;
    labelFn?: (item: PagedSelectRawOption) => unknown;
    maxTagCount?: SelectProps['maxTagCount'];
    mode?: PagedSelectMode;
    multiple?: boolean;
    numberToString?: boolean;
    options?: PagedSelectRawOption[];
    pageParam?: string;
    pageSize?: number;
    pageSizeOptions?: string[];
    pageSizeParam?: string;
    pagination?: boolean;
    params?: Record<string, any>;
    placeholder?: string;
    resultField?: string;
    searchDelay?: number;
    selectedOptions?: PagedSelectRawOption[];
    showSizeChanger?: boolean;
    showTotal?: boolean;
    simplePagination?: boolean;
    size?: SelectProps['size'];
    status?: SelectProps['status'];
    totalField?: string;
    value?: PagedSelectValue;
    valueField?: string;
  }>(),
  {
    allowClear: true,
    alwaysLoad: false,
    alwaysShowPagination: false,
    api: undefined,
    disabled: false,
    disabledField: 'disabled',
    fetchSelectedApi: undefined,
    immediate: false,
    keywordParam: 'keyword',
    labelField: 'label',
    labelFn: undefined,
    maxTagCount: undefined,
    mode: undefined,
    multiple: false,
    numberToString: false,
    options: () => [],
    pageParam: 'page',
    pageSize: 20,
    pageSizeOptions: () => ['10', '20', '50', '100'],
    pageSizeParam: 'size',
    pagination: true,
    params: () => ({}),
    placeholder: '请选择',
    resultField: 'items',
    searchDelay: 300,
    selectedOptions: () => [],
    showSizeChanger: false,
    showTotal: true,
    simplePagination: true,
    size: undefined,
    status: undefined,
    totalField: undefined,
    value: undefined,
    valueField: 'value',
  },
);
const emit = defineEmits<{
  change: [
    value: PagedSelectValue,
    selectedOptions: PagedSelectRawOption[],
  ];
  dropdownVisibleChange: [open: boolean];
  optionsChange: [options: PagedSelectOption[]];
  pageChange: [page: number, pageSize: number, total: number];
  search: [keyword: string];
  'update:value': [value: PagedSelectValue];
}>();
const DEFAULT_RESULT_FIELDS = [
  'items',
  'records',
  'list',
  'rows',
  'data.items',
  'data.records',
  'data.list',
  'data.rows',
  'data',
];
const DEFAULT_TOTAL_FIELDS = [
  'total',
  'totalCount',
  'count',
  'data.total',
  'data.totalCount',
  'data.count',
];

const VNodes = defineComponent({
  name: 'PagedSelectVNodes',
  props: {
    vnodes: {
      default: null,
      type: null as unknown as PropType<VNodeChild>,
    },
  },
  setup(props) {
    return () => props.vnodes;
  },
});

const loading = ref(false);
const loadedOnce = ref(false);
const searchKeyword = ref('');
const optionMap = ref(new Map<string, PagedSelectOption>());
const currentPageOptionKeys = ref<string[]>([]);
const innerParams = ref<Record<string, any>>({});
const currentRequestId = ref(0);
const paginationState = reactive({
  current: 1,
  pageSize: props.pageSize,
  total: 0,
});
let searchTimer: ReturnType<typeof setTimeout> | undefined;

const resolvedMode = computed<PagedSelectMode | undefined>(() =>
  props.mode ?? (props.multiple ? 'multiple' : undefined),
);

const selectedValues = computed(() => normalizeValue(props.value));
const selectedValueKeys = computed(() => selectedValues.value.map(toValueKey));
const selectValue = computed<PagedSelectValue>(() => props.value ?? undefined);
const showPagination = computed(
  () =>
    props.pagination &&
    (props.alwaysShowPagination ||
      paginationState.total > paginationState.pageSize),
);
const showFooter = computed(() => props.showTotal || showPagination.value);

const selectOptions = computed<PagedSelectOption[]>(() => {
  const addedKeys = new Set<string>();
  const options: PagedSelectOption[] = [];

  for (const key of [...selectedValueKeys.value, ...currentPageOptionKeys.value]) {
    if (addedKeys.has(key)) {
      continue;
    }

    const option = optionMap.value.get(key) ?? getFallbackSelectedOption(key);
    if (!option) {
      continue;
    }

    addedKeys.add(key);
    options.push(option);
  }

  return options;
});

watch(
  () => props.value,
  () => {
    void ensureSelectedOptions();
  },
  { immediate: true },
);

watch(
  () => props.selectedOptions,
  (options) => {
    upsertOptions(options);
  },
  { deep: true, immediate: true },
);

watch(
  () => props.options,
  () => {
    if (!props.api) {
      loadStaticPage(1, paginationState.pageSize, searchKeyword.value);
    }
  },
  { deep: true, immediate: true },
);

watch(
  () => props.params,
  () => {
    if (loadedOnce.value || props.immediate) {
      void loadPage(1, paginationState.pageSize, searchKeyword.value);
    }
  },
  { deep: true },
);

watch(
  () => props.pageSize,
  (pageSize) => {
    paginationState.pageSize = pageSize;
    if (loadedOnce.value || props.immediate) {
      void loadPage(1, pageSize, searchKeyword.value);
    }
  },
);

watch(selectOptions, (options) => {
  emit('optionsChange', options);
});

onMounted(() => {
  if (props.immediate) {
    void loadPage(1, paginationState.pageSize, searchKeyword.value);
  }
});

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
});

function getByPath(source: unknown, path?: string) {
  if (!path || source == null) {
    return source;
  }

  return path.split('.').reduce<unknown>((value, key) => {
    if (value == null || typeof value !== 'object') {
      return undefined;
    }
    return (value as Record<string, unknown>)[key];
  }, source);
}

function normalizeValue(value: PagedSelectValue): PagedSelectRawValue[] {
  if (Array.isArray(value)) {
    return value
      .map(getValueFromSelectValue)
      .filter((item): item is PagedSelectRawValue => item !== undefined);
  }

  const rawValue = getValueFromSelectValue(value);
  return rawValue === undefined ? [] : [rawValue];
}

function getValueFromSelectValue(value: null | PagedSelectValue) {
  if (value == null) {
    return undefined;
  }

  if (typeof value === 'number' || typeof value === 'string') {
    return value;
  }

  if (!Array.isArray(value) && typeof value === 'object') {
    const valueRecord = value as unknown as Record<string, unknown>;
    const rawValue = valueRecord.value ?? valueRecord.key;
    if (typeof rawValue === 'number' || typeof rawValue === 'string') {
      return rawValue;
    }
  }

  return undefined;
}

function toValueKey(value: PagedSelectRawValue) {
  return `${typeof value}:${value}`;
}

function normalizeOptionValue(value: unknown) {
  if (typeof value !== 'number' && typeof value !== 'string') {
    return undefined;
  }

  return props.numberToString ? String(value) : value;
}

function toSelectOption(raw: PagedSelectRawOption): PagedSelectOption | undefined {
  const value = normalizeOptionValue(getByPath(raw, props.valueField));
  if (value === undefined) {
    return undefined;
  }

  const label = props.labelFn?.(raw) ?? getByPath(raw, props.labelField) ?? String(value);

  return {
    ...raw,
    disabled: Boolean(getByPath(raw, props.disabledField)),
    label,
    raw,
    value,
  };
}

function upsertOptions(rawOptions: PagedSelectRawOption[] = []) {
  if (rawOptions.length === 0) {
    return;
  }

  const nextMap = new Map(optionMap.value);
  for (const rawOption of rawOptions) {
    const option = toSelectOption(rawOption);
    if (!option) {
      continue;
    }

    nextMap.set(toValueKey(option.value), option);
  }
  optionMap.value = nextMap;
}

function setCurrentPageOptions(rawOptions: PagedSelectRawOption[]) {
  upsertOptions(rawOptions);
  currentPageOptionKeys.value = rawOptions
    .map(toSelectOption)
    .filter((option): option is PagedSelectOption => Boolean(option))
    .map((option) => toValueKey(option.value));
}

function getFallbackSelectedOption(key: string) {
  const value = selectedValues.value.find((item) => toValueKey(item) === key);
  if (value === undefined) {
    return undefined;
  }

  return {
    disabled: false,
    label: String(value),
    raw: {
      [props.labelField]: String(value),
      [props.valueField]: value,
    },
    value,
  } satisfies PagedSelectOption;
}

function extractItems(result: PagedSelectApiResult): PagedSelectRawOption[] {
  if (Array.isArray(result)) {
    return result;
  }

  const explicitItems = getByPath(result, props.resultField);
  if (Array.isArray(explicitItems)) {
    return explicitItems;
  }

  for (const field of DEFAULT_RESULT_FIELDS) {
    const items = getByPath(result, field);
    if (Array.isArray(items)) {
      return items;
    }
  }

  return [];
}

function extractTotal(result: PagedSelectApiResult, fallbackTotal: number) {
  if (Array.isArray(result)) {
    return fallbackTotal;
  }

  const explicitTotal = getByPath(result, props.totalField);
  if (typeof explicitTotal === 'number') {
    return explicitTotal;
  }

  for (const field of DEFAULT_TOTAL_FIELDS) {
    const total = getByPath(result, field);
    if (typeof total === 'number') {
      return total;
    }
  }

  return fallbackTotal;
}

function matchesKeyword(rawOption: PagedSelectRawOption, keyword: string) {
  if (!keyword) {
    return true;
  }

  const option = toSelectOption(rawOption);
  return option
    ? String(option.label).toLowerCase().includes(keyword.toLowerCase())
    : false;
}

function loadStaticPage(page: number, pageSize: number, keyword = '') {
  const filteredOptions = props.options.filter((item) =>
    matchesKeyword(item, keyword.trim()),
  );
  const start = (page - 1) * pageSize;
  const pageOptions = filteredOptions.slice(start, start + pageSize);

  setCurrentPageOptions(pageOptions);
  paginationState.current = page;
  paginationState.pageSize = pageSize;
  paginationState.total = filteredOptions.length;
  loadedOnce.value = true;
  emit('pageChange', page, pageSize, paginationState.total);
}

function buildRequestParams(page: number, pageSize: number, keyword = '') {
  const trimmedKeyword = keyword.trim();

  return {
    ...props.params,
    ...innerParams.value,
    [props.keywordParam]: trimmedKeyword || undefined,
    [props.pageParam]: page,
    [props.pageSizeParam]: pageSize,
  };
}

async function loadPage(
  page = paginationState.current,
  pageSize = paginationState.pageSize,
  keyword = searchKeyword.value,
) {
  if (!props.api) {
    loadStaticPage(page, pageSize, keyword);
    return;
  }

  const requestId = currentRequestId.value + 1;
  currentRequestId.value = requestId;
  loading.value = true;

  try {
    const result = await props.api(buildRequestParams(page, pageSize, keyword));

    if (requestId !== currentRequestId.value) {
      return;
    }

    const items = extractItems(result);
    const total = extractTotal(result, items.length);

    setCurrentPageOptions(items);
    paginationState.current = page;
    paginationState.pageSize = pageSize;
    paginationState.total = total;
    loadedOnce.value = true;
    emit('pageChange', page, pageSize, total);
  } finally {
    if (requestId === currentRequestId.value) {
      loading.value = false;
    }
  }
}

async function ensureSelectedOptions() {
  if (!props.fetchSelectedApi) {
    return;
  }

  const missingValues = selectedValues.value.filter(
    (value) => !optionMap.value.has(toValueKey(value)),
  );

  if (missingValues.length === 0) {
    return;
  }

  try {
    const result = await props.fetchSelectedApi(missingValues);
    upsertOptions(extractItems(result));
  } catch {
    // Keep value rendering fallback when selected option lookup fails.
  }
}

function loadPageIfNeeded() {
  if (!loading.value && (props.alwaysLoad || !loadedOnce.value)) {
    void loadPage(1, paginationState.pageSize, searchKeyword.value);
  }
}

function getSelectedRawOptions(value: PagedSelectValue) {
  return normalizeValue(value)
    .map((item) => optionMap.value.get(toValueKey(item))?.raw)
    .filter((item): item is PagedSelectRawOption => Boolean(item));
}

function handleSearch(keyword: string) {
  searchKeyword.value = keyword;
  emit('search', keyword);

  if (searchTimer) {
    clearTimeout(searchTimer);
  }

  searchTimer = setTimeout(() => {
    void loadPage(1, paginationState.pageSize, keyword);
  }, props.searchDelay);
}

const handleChange: NonNullable<SelectProps['onChange']> = (value) => {
  emit('update:value', value);
  emit('change', value, getSelectedRawOptions(value));
};

function handleDropdownVisibleChange(open: boolean) {
  emit('dropdownVisibleChange', open);
  if (open) {
    loadPageIfNeeded();
  }
}

function handlePageChange(page: number, pageSize?: number) {
  void loadPage(page, pageSize ?? paginationState.pageSize, searchKeyword.value);
}

function reload() {
  return loadPage(paginationState.current, paginationState.pageSize, searchKeyword.value);
}

function reset() {
  searchKeyword.value = '';
  return loadPage(1, paginationState.pageSize, '');
}

defineExpose({
  getOptions: () => selectOptions.value,
  getRawOptions: () => selectOptions.value.map((option) => option.raw),
  getValue: () => props.value,
  reload,
  reset,
  updateParam(newParams: Record<string, any>) {
    innerParams.value = newParams;
    void loadPage(1, paginationState.pageSize, searchKeyword.value);
  },
});
</script>

<template>
  <Select
    v-bind="$attrs"
    :allow-clear="allowClear"
    :disabled="disabled"
    :filter-option="false"
    :loading="loading"
    :max-tag-count="maxTagCount"
    :mode="resolvedMode"
    :options="selectOptions"
    :placeholder="placeholder"
    :show-search="true"
    :size="size"
    :status="status"
    :value="selectValue"
    class="trio-paged-select"
    option-filter-prop="label"
    @change="handleChange"
    @dropdown-visible-change="handleDropdownVisibleChange"
    @focus="loadPageIfNeeded"
    @search="handleSearch"
  >
    <template v-if="loading" #notFoundContent>
      <div class="trio-paged-select__loading">
        <Spin size="small" />
      </div>
    </template>

    <template #dropdownRender="{ menuNode }">
      <div class="trio-paged-select__dropdown">
        <VNodes :vnodes="menuNode" />
        <div
          v-if="showFooter"
          class="trio-paged-select__footer"
          @mousedown.prevent.stop
        >
          <span v-if="showTotal" class="trio-paged-select__total">
            共 {{ paginationState.total }} 条
          </span>
          <Pagination
            v-if="showPagination"
            :current="paginationState.current"
            :page-size="paginationState.pageSize"
            :page-size-options="pageSizeOptions"
            :show-less-items="true"
            :show-size-changer="showSizeChanger"
            :simple="simplePagination"
            :total="paginationState.total"
            size="small"
            @change="handlePageChange"
          />
        </div>
      </div>
    </template>
  </Select>
</template>

<style scoped>
.trio-paged-select {
  width: 100%;
}

.trio-paged-select__loading {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

.trio-paged-select__footer {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid var(--ant-color-border-secondary, #f0f0f0);
  background: var(--ant-color-bg-container, #fff);
}

.trio-paged-select__total {
  flex: 0 0 auto;
  color: var(--ant-color-text-tertiary, rgb(0 0 0 / 45%));
  font-size: 12px;
  white-space: nowrap;
}

.trio-paged-select__footer :deep(.ant-pagination) {
  margin: 0;
  margin-left: auto;
}
</style>
