<script setup lang="ts">
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, message, Modal, Space } from 'ant-design-vue';

import { createProcessPackage } from '#/api/process';

import FlowDesigner from '../components/FlowDesigner.vue';
import { validateProcessDefinition } from '../components/process-designer';

const designerRef = ref<InstanceType<typeof FlowDesigner>>();

const editJson = ref('');
const jsonModalOpen = ref(false);

function handleExport() {
  const json = designerRef.value?.doExport();
  if (!json) return;
  editJson.value = json;
  jsonModalOpen.value = true;
}

async function handleSaveAsPackage() {
  if (!editJson.value) return;
  const validationErrors = validateProcessDefinition(editJson.value);
  if (validationErrors.length > 0) {
    message.error(validationErrors[0]);
    return;
  }
  try {
    const pkg = JSON.parse(editJson.value);
    const processKey = pkg?.processKey || `design_${Date.now()}`;
    const name = pkg?.flow?.nodes?.find((n: any) => n.type === 'START')?.name || '未命名流程';

    await createProcessPackage({
      processKey,
      name,
      category: 'approval',
      processJson: editJson.value,
    });
    message.success('流程包已保存');
    jsonModalOpen.value = false;
  } catch {
    message.warning('JSON 格式不正确');
  }
}

function handleJsonChange(json: string) {
  editJson.value = json;
}
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full flex-col gap-3 p-3">
      <!-- 页面工具栏 -->
      <div class="flex items-center justify-between flex-shrink-0">
        <div class="flex items-center gap-3">
          <h2 class="text-base font-semibold m-0">流程设计器 (X6)</h2>
        </div>
        <Space>
          <Button @click="handleExport">导出 JSON</Button>
        </Space>
      </div>

      <!-- 设计器组件 -->
      <div class="flex-1 min-h-0">
        <FlowDesigner ref="designerRef" @change="handleJsonChange" />
      </div>
    </div>

    <Modal v-model:open="jsonModalOpen" title="流程包 JSON" width="800" :on-ok="handleSaveAsPackage" ok-text="保存为流程包">
      <textarea
        v-model="editJson"
        class="json-editor"
        rows="20"
      />
    </Modal>
  </Page>
</template>

<style scoped>
.json-editor {
  width: 100%;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.6;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  resize: vertical;
  outline: none;
}
.json-editor:focus {
  border-color: #1677ff;
}
</style>
