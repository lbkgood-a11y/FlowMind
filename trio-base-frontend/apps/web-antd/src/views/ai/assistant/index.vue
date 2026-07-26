<script setup lang="ts">
import { computed } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Descriptions,
  DescriptionsItem,
  Space,
  Tag,
} from 'ant-design-vue';

import { useAgentStore } from '#/store';

defineOptions({ name: 'AiAssistantWorkbench' });

const store = useAgentStore();
const assistantEnabled = computed(
  () =>
    import.meta.env.VITE_AGENT_ASSISTANT_ENABLED === 'true' ||
    (import.meta.env.DEV &&
      import.meta.env.VITE_AGENT_ASSISTANT_ENABLED !== 'false'),
);
const activeStatus = computed(() => store.activeRun?.status ?? 'IDLE');

function openAssistant() {
  store.open = true;
}
</script>

<template>
  <Page auto-content-height>
    <div class="ai-assistant-page">
      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>AI 助手</h2>
            <p>Agent 运行入口与会话状态</p>
          </div>
          <Space>
            <Tag :color="assistantEnabled ? 'green' : 'orange'">
              {{ assistantEnabled ? '已启用' : '未启用' }}
            </Tag>
            <Button
              :disabled="!assistantEnabled"
              type="primary"
              @click="openAssistant"
            >
              打开助手
            </Button>
          </Space>
        </div>

        <Alert
          v-if="!assistantEnabled"
          class="mb-4"
          message="当前构建未启用 AI 助手"
          show-icon
          type="warning"
        />

        <Descriptions bordered :column="1" size="small">
          <DescriptionsItem label="前端入口">
            <Tag color="blue">/ai/assistant</Tag>
          </DescriptionsItem>
          <DescriptionsItem label="Agent API">
            <Tag>/api/v1/agent/runs</Tag>
          </DescriptionsItem>
          <DescriptionsItem label="事件通道">
            <Tag>/api/v1/agent/runs/:runId/events</Tag>
          </DescriptionsItem>
          <DescriptionsItem label="当前会话">
            <Space>
              <Tag :color="store.activeRun ? 'processing' : 'default'">
                {{ activeStatus }}
              </Tag>
              <span v-if="store.activeRun">{{ store.activeRun.runId }}</span>
            </Space>
          </DescriptionsItem>
          <DescriptionsItem label="最近错误" v-if="store.error">
            <Space direction="vertical" size="small">
              <Tag color="red">{{ store.error.code }}</Tag>
              <span>{{ store.error.message }}</span>
            </Space>
          </DescriptionsItem>
        </Descriptions>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.ai-assistant-page {
  min-height: 100%;
  padding: 16px;
}

.panel {
  border: 1px solid var(--ant-color-border-secondary, #f0f0f0);
  border-radius: 8px;
  background: var(--ant-color-bg-container, #fff);
  padding: 16px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
}

.panel-header p {
  margin: 4px 0 0;
  color: var(--ant-color-text-secondary, rgb(0 0 0 / 45%));
}

@media (max-width: 640px) {
  .panel-header {
    flex-direction: column;
  }
}
</style>
