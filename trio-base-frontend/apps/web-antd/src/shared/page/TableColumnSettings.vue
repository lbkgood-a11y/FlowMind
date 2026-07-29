<script setup lang="ts">
import type { TableColumnSetting } from './table-column-settings';

import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Checkbox, Popover, Space, Tooltip } from 'ant-design-vue';

const props = defineProps<{
  defaults: TableColumnSetting[];
  modelValue: TableColumnSetting[];
  storageKey: string;
}>();

const emit = defineEmits<{
  apply: [settings: TableColumnSetting[]];
}>();

const open = ref(false);
const draft = ref<TableColumnSetting[]>([]);
const draggedIndex = ref<number>();

const allChecked = computed({
  get: () => draft.value.length > 0 && draft.value.every((item) => item.visible),
  set: (checked: boolean) => {
    draft.value.forEach((item) => {
      if (!item.required) item.visible = checked;
    });
  },
});

const partiallyChecked = computed(
  () => draft.value.some((item) => item.visible) && !allChecked.value,
);

function clone(settings: TableColumnSetting[]) {
  return settings.map((item) => ({ ...item }));
}

function normalize(settings: TableColumnSetting[]) {
  const rank = (item: TableColumnSetting) =>
    item.fixed === 'left' ? 0 : item.fixed === 'right' ? 2 : 1;
  return clone(settings).sort((left, right) => rank(left) - rank(right));
}

function handleOpenChange(nextOpen: boolean) {
  open.value = nextOpen;
  if (nextOpen) draft.value = clone(props.modelValue);
}

function restoreDefaults() {
  draft.value = clone(props.defaults);
}

function cancel() {
  open.value = false;
}

function apply() {
  if (!draft.value.some((item) => item.visible)) return;
  const settings = normalize(draft.value);
  try {
    localStorage.setItem(props.storageKey, JSON.stringify(settings));
  } catch {
    // Storage can be unavailable in privacy mode; applying in-memory still works.
  }
  emit('apply', settings);
  open.value = false;
}

function toggleFixed(index: number, fixed: 'left' | 'right') {
  const item = draft.value[index];
  if (!item) return;
  item.fixed = item.fixed === fixed ? undefined : fixed;
}

function dragStart(index: number) {
  draggedIndex.value = index;
}

function drop(targetIndex: number) {
  const sourceIndex = draggedIndex.value;
  draggedIndex.value = undefined;
  if (sourceIndex === undefined || sourceIndex === targetIndex) return;
  const [item] = draft.value.splice(sourceIndex, 1);
  if (item) draft.value.splice(targetIndex, 0, item);
}
</script>

<template>
  <Popover
    :open="open"
    overlay-class-name="global-column-settings-popover"
    placement="bottomRight"
    trigger="click"
    @open-change="handleOpenChange"
  >
    <template #content>
      <div class="column-settings-panel">
        <Checkbox
          v-model:checked="allChecked"
          class="column-check-all"
          :indeterminate="partiallyChecked"
        >
          全部
        </Checkbox>
        <div class="column-setting-list">
          <div
            v-for="(item, index) in draft"
            :key="item.key"
            class="column-setting-item"
            draggable="true"
            @dragover.prevent
            @dragstart="dragStart(index)"
            @drop="drop(index)"
          >
            <Checkbox v-model:checked="item.visible" :disabled="item.required" />
            <IconifyIcon icon="lucide:grip-vertical" class="drag-icon" />
            <span class="column-setting-title" :title="item.title">{{ item.title }}</span>
            <Tooltip title="固定到左侧">
              <button
                :class="{ active: item.fixed === 'left' }"
                class="pin-button"
                type="button"
                @click="toggleFixed(index, 'left')"
              >
                <IconifyIcon icon="lucide:pin" />
              </button>
            </Tooltip>
            <Tooltip title="固定到右侧">
              <button
                :class="{ active: item.fixed === 'right' }"
                class="pin-button"
                type="button"
                @click="toggleFixed(index, 'right')"
              >
                <IconifyIcon icon="lucide:pin" class="rotate-pin" />
              </button>
            </Tooltip>
          </div>
        </div>
        <div class="column-setting-footer">
          <Button type="link" @click="restoreDefaults">恢复默认</Button>
          <Space :size="4">
            <Button type="text" @click="cancel">取消</Button>
            <Button type="link" @click="apply">确认</Button>
          </Space>
        </div>
      </div>
    </template>
    <Tooltip title="列设置">
      <Button :class="{ 'is-active': open }" class="column-setting-trigger" shape="circle">
        <i aria-hidden="true" class="vxe-button--item vxe-table-icon-custom"></i>
      </Button>
    </Tooltip>
  </Popover>
</template>

<style scoped>
.column-setting-trigger.is-active {
  color: var(--ant-color-primary);
  border-color: var(--ant-color-primary);
}

.column-settings-panel {
  width: 244px;
  color: #1f2937;
}

.column-check-all {
  display: flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  font-weight: 600;
  color: #3164f4;
}

.column-setting-list {
  max-height: 320px;
  padding: 2px 0 6px;
  overflow-y: auto;
}

.column-setting-item {
  display: grid;
  grid-template-columns: 18px 18px minmax(0, 1fr) 24px 24px;
  column-gap: 4px;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  font-size: 14px;
  cursor: grab;
}

.column-setting-item:hover {
  background: #f6f8ff;
}

.drag-icon {
  color: #6b7280;
}

.column-setting-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pin-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  color: #9ca3af;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.pin-button:hover,
.pin-button.active {
  color: #3164f4;
}

.rotate-pin {
  transform: rotate(90deg);
}

.column-setting-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  padding: 0 10px;
  border-top: 1px solid #edf0f5;
}

:global(.global-column-settings-popover .ant-popover-inner) {
  padding: 0;
  border-radius: 4px;
}
</style>
