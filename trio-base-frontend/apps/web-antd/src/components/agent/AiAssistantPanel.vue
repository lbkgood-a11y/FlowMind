<script lang="ts" setup>
import type { ActionApi } from '#/api/action-client';

import { computed, onBeforeUnmount, ref } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Empty,
  Input,
  List,
  ListItem,
  Space,
  Tag,
  TypographyLink,
  TypographyParagraph,
  TypographyText,
} from 'ant-design-vue';

import { dispatchActionCandidate } from '#/api/action-client';
import {
  createAgentRun,
  resumeAgentRun,
  subscribeAgentRunEvents,
} from '#/api/agent';
import ActionConfirmationWrapper from '#/shared/action/ActionConfirmationWrapper.vue';
import ActionResultFeedback from '#/shared/action/ActionResultFeedback.vue';
import { useAgentStore } from '#/store';

defineOptions({ name: 'AiAssistantPanel' });

const route = useRoute();
const userStore = useUserStore();
const store = useAgentStore();
const input = ref('');
const supplement = ref('');
const submitting = ref(false);
const enabled =
  import.meta.env.VITE_AGENT_ASSISTANT_ENABLED === 'true' ||
  (import.meta.env.DEV &&
    import.meta.env.VITE_AGENT_ASSISTANT_ENABLED !== 'false');
let streamController: AbortController | undefined;

const candidateData = computed(() => {
  const data = store.pendingCandidate?.payload?.data;
  return data && typeof data === 'object'
    ? Object.entries(data as Record<string, unknown>)
    : [];
});

async function send() {
  const message = input.value.trim();
  if (!message || submitting.value) return;
  submitting.value = true;
  try {
    const run = await createAgentRun({
      message,
      pageContext: {
        appKey:
          typeof route.params.appKey === 'string'
            ? route.params.appKey
            : undefined,
        objectId:
          typeof route.params.id === 'string' ? route.params.id : undefined,
        route: route.fullPath,
      },
    });
    store.begin(run, message);
    input.value = '';
    startStream(run.runId, run.lastSequence);
  } catch {
    store.setError('AGENT_RUN_CREATE_FAILED', '无法启动 AI 助手，请稍后重试');
  } finally {
    submitting.value = false;
  }
}

function startStream(runId: string, cursor: number) {
  streamController?.abort();
  streamController = new AbortController();
  void subscribeAgentRunEvents(runId, {
    cursor,
    onEvent: store.applyEvent,
    signal: streamController.signal,
  }).catch(() => {
    if (!streamController?.signal.aborted) {
      store.setError('AGENT_STREAM_FAILED', 'AI 消息连接已中断，请重新发送');
    }
  });
}

async function submitSupplement() {
  const run = store.activeRun;
  const text = supplement.value.trim();
  if (!run || !text || submitting.value) return;
  submitting.value = true;
  try {
    const updated = await resumeAgentRun(run.runId, {
      kind: 'input',
      values: { message: text },
    });
    store.setRun(updated);
    supplement.value = '';
  } catch {
    store.setError('AGENT_RESUME_FAILED', '补充信息提交失败');
  } finally {
    submitting.value = false;
  }
}

async function confirmCandidate() {
  const run = store.activeRun;
  const candidate = store.pendingCandidate;
  if (!run || !candidate || submitting.value) return;
  submitting.value = true;
  try {
    const now = new Date().toISOString();
    const confirmed: ActionApi.ActionCandidate = {
      ...candidate,
      actor: {
        ...candidate.actor,
        displayName:
          userStore.userInfo?.realName || userStore.userInfo?.username,
        id: userStore.userInfo?.userId,
        tenantId: candidate.target?.tenantId ?? 'default',
        type: 'USER',
      },
      context: {
        ...candidate.context,
        confirmationId: globalThis.crypto.randomUUID(),
        confirmedAt: now,
        confirmedBy: userStore.userInfo?.userId,
      },
      source: 'LUI',
    };
    const result = await dispatchActionCandidate(confirmed);
    store.setActionResult(result);
    const updated = await resumeAgentRun(run.runId, {
      kind: 'action_result',
      values: result as Record<string, unknown>,
    });
    store.setRun(updated);
  } catch {
    store.setError('ACTION_DISPATCH_FAILED', '提交失败，未重复执行该操作');
  } finally {
    submitting.value = false;
  }
}

async function cancelPending() {
  const run = store.activeRun;
  if (!run || submitting.value) return;
  submitting.value = true;
  try {
    const updated = await resumeAgentRun(run.runId, {
      kind: 'cancel',
      values: {},
    });
    store.setRun(updated);
  } finally {
    submitting.value = false;
  }
}

onBeforeUnmount(() => streamController?.abort());
</script>

<template>
  <Button
    v-if="enabled"
    class="tb-ai-assistant-trigger"
    type="primary"
    @click="store.open = true"
  >
    AI 助手
  </Button>

  <Drawer
    v-if="enabled"
    v-model:open="store.open"
    :destroy-on-close="false"
    placement="right"
    title="TrioBase AI 助手"
    width="440"
  >
    <div class="tb-ai-assistant">
      <Alert
        description="当前优先支持请假申请。所有业务操作都会先展示预览，并在确认后通过统一 Action 执行。"
        message="安全执行模式"
        show-icon
        type="info"
      />

      <div class="tb-ai-assistant__conversation">
        <Empty v-if="store.messages.length === 0" description="试试：帮我申请明天一天事假，因为家中有事" />
        <div
          v-for="message in store.messages"
          :key="message.id"
          class="tb-ai-message" :class="[`tb-ai-message--${message.role}`]"
        >
          {{ message.text }}
        </div>

        <Card v-if="store.evidence.length" size="small" title="参考资料">
          <List :data-source="store.evidence" size="small">
            <template #renderItem="{ item }">
              <ListItem>
                <div>
                  <TypographyLink v-if="item.uri" :href="item.uri" target="_blank">
                    {{ item.title }}
                  </TypographyLink>
                  <TypographyText v-else strong>{{ item.title }}</TypographyText>
                  <TypographyParagraph v-if="item.excerpt" ellipsis>
                    {{ item.excerpt }}
                  </TypographyParagraph>
                </div>
              </ListItem>
            </template>
          </List>
        </Card>

        <Card v-if="store.missingInput" size="small" title="需要补充信息">
          <TypographyParagraph>{{ store.missingInput.prompt }}</TypographyParagraph>
          <div class="tb-ai-compact">
            <Input
              v-model:value="supplement"
              :placeholder="`请补充：${store.missingInput.missingSlots.join('、')}`"
              @press-enter="submitSupplement"
            />
            <Button :loading="submitting" type="primary" @click="submitSupplement">继续</Button>
          </div>
        </Card>

        <Card v-if="store.pendingCandidate" size="small" title="操作预览">
          <template #extra>
            <Tag color="blue">{{ store.pendingCandidate.actionType }}</Tag>
          </template>
          <Descriptions bordered :column="1" size="small">
            <DescriptionsItem
              v-for="[key, value] in candidateData"
              :key="key"
              :label="key"
            >
              {{ value }}
            </DescriptionsItem>
          </Descriptions>
          <Space class="mt-3">
            <ActionConfirmationWrapper
              :confirmation="store.pendingCandidate.confirmation ?? true"
              @confirmed="confirmCandidate"
            >
              <Button :loading="submitting" type="primary">确认并提交</Button>
            </ActionConfirmationWrapper>
            <Button :disabled="submitting" @click="cancelPending">取消</Button>
          </Space>
        </Card>

        <ActionResultFeedback :result="store.actionResult" />
        <Alert
          v-if="store.error"
          :description="store.error.code"
          :message="store.error.message"
          show-icon
          type="error"
        />
      </div>

      <div class="tb-ai-compact">
        <Input
          v-model:value="input"
          :disabled="store.busy || Boolean(store.pendingCandidate) || Boolean(store.missingInput)"
          placeholder="描述你要办理的业务"
          @press-enter="send"
        />
        <Button :loading="submitting || store.busy" type="primary" @click="send">发送</Button>
      </div>
    </div>
  </Drawer>
</template>

<style scoped>
.tb-ai-assistant-trigger {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1000;
  box-shadow: 0 6px 20px rgb(0 0 0 / 18%);
}

.tb-ai-assistant {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
}

.tb-ai-assistant__conversation {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  padding: 4px 2px;
}

.tb-ai-message {
  max-width: 88%;
  border-radius: 10px;
  padding: 9px 12px;
  white-space: pre-wrap;
}

.tb-ai-message--assistant {
  align-self: flex-start;
  background: var(--ant-color-fill-tertiary, #f5f5f5);
}

.tb-ai-message--user {
  align-self: flex-end;
  background: var(--ant-color-primary-bg, #e6f4ff);
}

.tb-ai-compact {
  display: flex;
  width: 100%;
}

.tb-ai-compact :deep(.ant-input) {
  min-width: 0;
  flex: 1;
}
</style>
