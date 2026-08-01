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

const AGENT_ERROR_MESSAGES: Record<string, string> = {
  AGENT_RUN_CREATE_FAILED:
    'Agent Run 创建失败，请检查 ai-agent-orchestrator 数据库连接和运行状态',
  AGENT_RUN_NOT_FOUND: 'Agent Run 不存在，或当前账号无权访问该会话',
  AGENT_RUNTIME_DISABLED: 'AI Agent 运行时未启用，请检查 AGENT_ENABLED 配置',
  HTTP_401: '登录态已失效，或请求没有经过 platform-gateway',
  HTTP_403: '当前账号未获得 AI 助手访问授权',
  HTTP_500: 'AI Agent 服务内部异常，请查看 ai-agent-orchestrator 日志',
  HTTP_503: 'AI Agent 服务暂不可用，请确认服务已启动',
  TENANT_NOT_AVAILABLE: '当前租户暂未开放 AI 助手',
  TRACE_CONTEXT_REQUIRED: '请求缺少 TraceId，请确认网关 TraceIdFilter 已启用',
  TRUSTED_USER_CONTEXT_REQUIRED:
    '请求缺少可信用户上下文，请通过 platform-gateway 访问并重新登录',
};

const candidateData = computed(() => {
  const data = store.pendingCandidate?.payload?.data;
  return data && typeof data === 'object'
    ? Object.entries(data as Record<string, unknown>)
    : [];
});

function resolveAgentRequestError(
  error: unknown,
  fallbackCode: string,
  fallbackMessage: string,
) {
  const payload = responsePayload(error);
  const statusCode = responseStatus(error);
  const statusErrorCode = statusCode ? `HTTP_${statusCode}` : undefined;
  const code =
    findErrorCode(payload) ??
    findErrorCode(error) ??
    statusErrorCode ??
    fallbackCode;
  const rawMessage = findErrorMessage(payload) ?? findErrorMessage(error);
  return {
    code,
    message: AGENT_ERROR_MESSAGES[code] ?? rawMessage ?? fallbackMessage,
  };
}

function responsePayload(error: unknown) {
  if (!isRecord(error)) return undefined;
  const response = error.response;
  if (isRecord(response) && 'data' in response) {
    return response.data;
  }
  if ('data' in error) {
    return error.data;
  }
  return undefined;
}

function responseStatus(error: unknown) {
  if (!isRecord(error)) return undefined;
  const response = error.response;
  if (!isRecord(response)) return undefined;
  return typeof response.status === 'number' ? response.status : undefined;
}

function findErrorCode(source: unknown) {
  return [readErrorField(source, 'code'), readErrorField(source, 'error'), readErrorField(source, 'detail')]
    .map(errorText)
    .find((value) => value && /^[A-Z][A-Z0-9_]*$/.test(value));
}

function findErrorMessage(source: unknown) {
  return (
    errorText(readErrorField(source, 'message')) ??
    errorText(readErrorField(source, 'detail')) ??
    errorText(readErrorField(source, 'error'))
  );
}

function readErrorField(source: unknown, key: string) {
  if (!isRecord(source)) return undefined;
  return source[key];
}

function errorText(value: unknown) {
  if (typeof value === 'string' && value.trim()) {
    return value.trim();
  }
  if (Array.isArray(value) && value.length > 0) {
    return JSON.stringify(value);
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

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
  } catch (error) {
    const resolved = resolveAgentRequestError(
      error,
      'AGENT_RUN_CREATE_FAILED',
      '无法启动 AI 助手，请稍后重试',
    );
    store.setError(resolved.code, resolved.message);
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
  }).catch((error) => {
    if (!streamController?.signal.aborted) {
      const resolved = resolveAgentRequestError(
        error,
        'AGENT_STREAM_FAILED',
        'AI 消息连接已中断，请重新发送',
      );
      store.setError(resolved.code, resolved.message);
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
  } catch (error) {
    const resolved = resolveAgentRequestError(
      error,
      'AGENT_RESUME_FAILED',
      '补充信息提交失败',
    );
    store.setError(resolved.code, resolved.message);
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
  } catch (error) {
    const resolved = resolveAgentRequestError(
      error,
      'ACTION_DISPATCH_FAILED',
      '提交失败，未重复执行该操作',
    );
    store.setError(resolved.code, resolved.message);
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
  } catch (error) {
    const resolved = resolveAgentRequestError(
      error,
      'AGENT_CANCEL_FAILED',
      '取消操作失败',
    );
    store.setError(resolved.code, resolved.message);
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
        description="业务工具由已发布应用注册。所有业务操作都会先展示预览，并在确认后通过统一 Action 执行。"
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
