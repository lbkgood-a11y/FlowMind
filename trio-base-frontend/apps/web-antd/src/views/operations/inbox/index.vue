<script setup lang="ts">
import type { InboxApi } from '#/api/inbox';

import { computed, h, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Checkbox,
  Empty,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
} from 'ant-design-vue';

import {
  archiveInboxItem,
  executeInboxAction,
  getInboxNavigation,
  getInboxPage,
  hideInboxItem,
  markInboxItemsRead,
  restoreInboxItem,
} from '#/api/inbox';
import { resolveActionComponent } from '#/registry/action-component-registry';
import {
  describeInboxAction,
  resolveInboxAction,
  resolveInboxNavigation,
} from '#/registry/inbox-interaction-registry';

import { buildInboxQuery, presentInboxItem } from './inbox-presentation';

const router = useRouter();

const activeView = ref('all');
const sourceOwner = ref<string>();
const loading = ref(false);
const loadError = ref('');
const items = ref<InboxApi.Item[]>([]);
const selectedIds = ref<string[]>([]);
const page = ref(1);
const hasMore = ref(false);

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读' },
  { key: 'task', label: '任务相关' },
  { key: 'notification', label: '通知' },
  { key: 'announcement', label: '公告' },
];
const selectedSet = computed(() => new Set(selectedIds.value));

async function load(reset = true) {
  loading.value = true;
  loadError.value = '';
  if (reset) page.value = 1;
  try {
    const result = await getInboxPage(
      buildInboxQuery({
        itemType:
          activeView.value === 'task'
            ? 'TASK'
            : activeView.value === 'notification'
              ? 'NOTIFICATION'
              : activeView.value === 'announcement'
                ? 'ANNOUNCEMENT'
                : undefined,
        page: page.value,
        readState: activeView.value === 'unread' ? 'UNREAD' : undefined,
        size: 20,
        sourceOwner: sourceOwner.value,
      }),
    );
    items.value = reset ? result.items : [...items.value, ...result.items];
    hasMore.value = result.hasMore;
    selectedIds.value = [];
  } catch {
    loadError.value = '消息加载失败，请检查网络后重试。';
  } finally {
    loading.value = false;
  }
}

async function loadMore() {
  page.value += 1;
  await load(false);
}

function toggle(id: string, checked: boolean) {
  selectedIds.value = checked
    ? [...selectedSet.value, id]
    : selectedIds.value.filter((value) => value !== id);
}

async function markSelectedRead() {
  if (!selectedIds.value.length) return;
  await markInboxItemsRead(selectedIds.value);
  message.success('已标记为已读');
  await load();
}

async function archive(item: InboxApi.Item) {
  await archiveInboxItem(item.id);
  await load();
}

async function restore(item: InboxApi.Item) {
  await restoreInboxItem(item.id);
  await load();
}

async function hide(item: InboxApi.Item) {
  await hideInboxItem(item.id);
  await load();
}

async function openResource(item: InboxApi.Item) {
  if (!item.resourceReference || !presentInboxItem(item).canInteract) return;
  try {
    const navigation = await getInboxNavigation(item.id);
    await router.push(
      resolveInboxNavigation(item.resourceReference, navigation),
    );
  } catch {
    message.warning('业务资源当前不可用或未在客户端注册');
  }
}

function confirmAction(item: InboxApi.Item) {
  if (!item.resourceReference?.actionId || !presentInboxItem(item).canInteract)
    return;
  try {
    const { actionId, registration } = describeInboxAction(
      item.resourceReference,
    );
    const candidateId = globalThis.crypto.randomUUID();
    const component = resolveActionComponent('ActionCandidateConfirmation', {
      actionType: actionId,
      candidateId,
      title: registration.title,
    });
    let reason = '';
    Modal.confirm({
      title: String(component.props.title),
      content:
        actionId === 'process.task.reject'
          ? () =>
              h(Input.TextArea, {
                'onUpdate:value': (value: string) => {
                  reason = value;
                },
                placeholder: '请输入驳回原因',
                rows: 3,
              })
          : '系统将在提交前重新向业务 Owner 校验权限和当前状态。',
      okText: registration.confirmLabel,
      onOk: async () => {
        const action = resolveInboxAction(
          item.resourceReference,
          actionId === 'process.task.reject' ? { reason } : {},
        );
        await executeInboxAction(item.id, {
          idempotencyKey: `inbox:${item.id}:${candidateId}`,
          payload: action.payload,
        });
        message.success('操作已提交，请以业务页面最新状态为准');
        await load();
      },
    });
  } catch {
    message.warning('该快捷操作未注册或参数不符合安全契约');
  }
}

onMounted(() => load());
</script>

<template>
  <Page title="消息中心" description="站内通知、业务提醒与公告的个人视图">
    <Card :bordered="false">
      <Tabs
        v-model:active-key="activeView"
        :items="tabs"
        @change="() => load()"
      />
      <div class="inbox-toolbar">
        <Space wrap>
          <Select
            v-model:value="sourceOwner"
            allow-clear
            placeholder="来源服务"
            :options="[
              { label: '运营服务', value: 'service-ops' },
              { label: '流程服务', value: 'service-workflow-engine' },
              { label: '低代码服务', value: 'service-lowcode' },
            ]"
            @change="() => load()"
          />
          <Button :disabled="!selectedIds.length" @click="markSelectedRead">
批量已读
</Button>
          <Button @click="load()">刷新</Button>
        </Space>
      </div>

      <Alert
        v-if="loadError"
        role="alert"
        show-icon
        type="error"
        :message="loadError"
      />

      <Spin :spinning="loading">
        <Empty v-if="!items.length && !loading" description="暂无消息" />
        <div v-else class="inbox-list" aria-live="polite">
          <article
            v-for="item in items"
            :key="item.id"
            :aria-label="presentInboxItem(item).ariaLabel"
            class="inbox-item"
            :class="{ unread: !item.readAt }"
          >
            <Checkbox
              :checked="selectedSet.has(item.id)"
              :aria-label="`选择消息 ${item.title}`"
              @change="(event) => toggle(item.id, event.target.checked)"
            />
            <div class="inbox-content">
              <Space wrap>
                <strong>{{ presentInboxItem(item).title }}</strong>
                <Tag>{{ item.itemType }}</Tag>
                <Tag v-if="item.taskRelated" color="orange">任务相关</Tag>
                <Tag v-if="item.archivedAt">已归档</Tag>
                <Tag v-if="!item.readAt" color="blue">未读</Tag>
                <Tag v-if="item.expired" color="default">已过期</Tag>
                <Tag v-if="item.sourceAvailable === false" color="orange">
来源不可用
</Tag>
              </Space>
              <p>{{ presentInboxItem(item).summary }}</p>
              <small>{{ new Date(item.receivedAt).toLocaleString() }}</small>
            </div>
            <Space class="inbox-actions" wrap>
              <Button
                v-if="
                  item.resourceReference && presentInboxItem(item).canInteract
                "
                :aria-label="`查看业务：${item.title}`"
                type="link"
                @click="openResource(item)"
                >
查看业务
</Button>
              <Button
                v-if="
                  item.resourceReference?.actionId &&
                  presentInboxItem(item).canInteract
                "
                :aria-label="`快捷处理：${item.title}`"
                type="link"
                @click="confirmAction(item)"
                >
快捷处理
</Button>
              <Button
                v-if="!item.readAt"
                type="link"
                @click="markInboxItemsRead([item.id]).then(() => load())"
              >
                标为已读
              </Button>
              <Button v-if="item.archivedAt" type="link" @click="restore(item)">
恢复
</Button>
              <Button v-else type="link" @click="archive(item)">归档</Button>
              <Popconfirm
                title="仅从你的消息中心隐藏，是否继续？"
                @confirm="hide(item)"
              >
                <Button danger type="link">隐藏</Button>
              </Popconfirm>
            </Space>
          </article>
        </div>
      </Spin>
      <div v-if="hasMore" class="load-more">
        <Button @click="loadMore">加载更多</Button>
      </div>
    </Card>
  </Page>
</template>

<style scoped>
.inbox-toolbar {
  margin-bottom: 16px;
}
.inbox-list {
  display: grid;
  gap: 10px;
}
.inbox-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  padding: 14px;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
}
.inbox-item.unread {
  border-left: 3px solid hsl(var(--primary));
  background: hsl(var(--accent) / 0.35);
}
.inbox-content p {
  margin: 8px 0;
  color: hsl(var(--muted-foreground));
}
.load-more {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
@media (max-width: 768px) {
  .inbox-item {
    grid-template-columns: auto 1fr;
  }
  .inbox-actions {
    grid-column: 2;
  }
}
</style>
