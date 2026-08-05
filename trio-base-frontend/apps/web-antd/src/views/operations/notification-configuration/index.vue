<script setup lang="ts">
import type { NotificationConfigurationApi as Api } from '#/api/notification-configuration';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Checkbox,
  FormItem,
  Input,
  message,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
} from 'ant-design-vue';

import {
  createNotificationTemplate,
  getNotificationChannels,
  getNotificationProviders,
  getNotificationRoutingPolicies,
  getNotificationTemplates,
  saveNotificationProvider,
  saveNotificationRoutingPolicy,
  setNotificationChannelEnabled,
  transitionNotificationTemplate,
  validateNotificationChannel,
} from '#/api/notification-configuration';

import {
  canEnableChannel,
  hasConfigurationPermission,
  safeCredentialLabel,
} from './configuration-policies';

const Textarea = Input.TextArea;
const access = useAccess();
const canManageChannels = computed(() =>
  hasConfigurationPermission(
    access.hasAccessByCodes,
    '/api/v2/notification-channels/**:PUT',
  ),
);
const canManageTemplates = computed(() =>
  hasConfigurationPermission(
    access.hasAccessByCodes,
    '/api/v2/notification-templates/**:PUT',
  ),
);
const loading = ref(false);
const loadError = ref('');
const tab = ref('channels');
const channels = ref<Api.Channel[]>([]);
const providers = ref<Api.Provider[]>([]);
const templates = ref<Api.Template[]>([]);
const policies = ref<Api.RoutingPolicy[]>([]);
const providerOpen = ref(false);
const templateOpen = ref(false);
const routingOpen = ref(false);
const channelCodes: Api.ChannelCode[] = [
  'IN_APP',
  'EMAIL',
  'SMS',
  'WE_COM',
  'DINGTALK',
];
const providerForm = reactive({
  channelCode: 'EMAIL' as Api.ChannelCode,
  credentialReference: '',
  displayName: '',
  providerKey: '',
});
const templateForm = reactive({
  bodyTemplate: '',
  channelCode: 'IN_APP' as Api.ChannelCode,
  localeCode: 'zh-CN',
  subjectTemplate: '',
  templateKey: '',
  variableSchemaJson: '{}',
});
const routingForm = reactive({
  categoryCode: '',
  fallbackEnabled: false,
  mandatoryCategory: false,
  orderedChannels: ['IN_APP'] as string[],
  priorityCode: 'NORMAL',
  quietEnd: '',
  quietStart: '',
  zoneId: 'Asia/Shanghai',
});
const stateColor: Record<string, string> = {
  DEGRADED: 'orange',
  DISABLED: 'default',
  INVALID: 'red',
  NOT_CONNECTED: 'default',
  READY: 'green',
};

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    [channels.value, providers.value, templates.value, policies.value] =
      await Promise.all([
        getNotificationChannels(),
        getNotificationProviders(),
        getNotificationTemplates(),
        getNotificationRoutingPolicies(),
      ]);
  } catch {
    loadError.value = '配置加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function toggleChannel(item: Api.Channel, enabled: boolean) {
  if (enabled && !canEnableChannel(item)) {
    message.warning('渠道尚未通过适配器与连通性校验，不能启用');
    return;
  }
  await setNotificationChannelEnabled(item.channelCode, enabled);
  await load();
}

async function validateChannel(item: Api.Channel) {
  const provider = providers.value.find(
    (value) => value.channelCode === item.channelCode,
  );
  await validateNotificationChannel(item.channelCode, provider?.providerKey);
  message.success('校验完成');
  await load();
}

async function saveProvider() {
  await saveNotificationProvider({ ...providerForm, settings: {} });
  providerOpen.value = false;
  message.success('供应商引用已保存');
  await load();
}

async function saveTemplate() {
  let variableSchema: Record<string, string>;
  try {
    variableSchema = JSON.parse(templateForm.variableSchemaJson);
  } catch {
    message.error('变量 Schema 必须是 JSON 对象');
    return;
  }
  await createNotificationTemplate({
    ...templateForm,
    variableSchema,
    variableSchemaJson: undefined,
  });
  templateOpen.value = false;
  message.success('模板草稿已创建');
  await load();
}

async function templateCommand(
  item: Api.Template,
  command: 'publish' | 'reject' | 'submit-review',
) {
  await transitionNotificationTemplate(item.versionId, command);
  await load();
}

async function saveRouting() {
  await saveNotificationRoutingPolicy({
    categoryCode: routingForm.categoryCode,
    fallbackEnabled: routingForm.fallbackEnabled,
    mandatoryCategory: routingForm.mandatoryCategory,
    orderedChannels: routingForm.orderedChannels,
    priorityCode: routingForm.priorityCode,
    quietHours:
      routingForm.quietStart && routingForm.quietEnd
        ? {
            start: routingForm.quietStart,
            end: routingForm.quietEnd,
            zoneId: routingForm.zoneId,
          }
        : undefined,
  });
  routingOpen.value = false;
  message.success('路由策略已保存');
  await load();
}

onMounted(load);
</script>

<template>
  <Page
    title="通知渠道配置"
    description="能力状态来自适配器校验，期望启用不代表渠道可投递"
  >
    <Alert
      class="notice"
      show-icon
      type="info"
      message="首期仅站内信具备真实投递能力；外部渠道在接入并校验前保持未接入。"
    />
    <Alert
      v-if="loadError"
      class="notice"
      role="alert"
      show-icon
      type="error"
      :message="loadError"
    />
    <Card :bordered="false">
      <Tabs
        v-model:active-key="tab"
        :items="[
          { key: 'channels', label: '渠道能力' },
          { key: 'providers', label: '供应商' },
          { key: 'templates', label: '模板版本' },
          { key: 'routing', label: '路由策略' },
        ]"
      />

      <Table
        v-if="tab === 'channels'"
        :data-source="channels"
        :loading="loading"
        row-key="channelCode"
        :pagination="false"
      >
        <Table.Column data-index="channelCode" title="渠道" /><Table.Column
          title="能力状态"
          >
<template #default="{ record }">
<Tag :color="stateColor[record.capabilityState]">
{{
              record.capabilityState
            }}
</Tag>
</template>
</Table.Column>
        <Table.Column title="适配器">
<template #default="{ record }">
{{
            record.adapterKey
              ? `${record.adapterKey} ${record.adapterVersion || ''}`
              : '未安装'
          }}
</template>
</Table.Column>
        <Table.Column title="期望启用">
<template #default="{ record }">
<Switch
              :checked="Boolean(record.desiredEnabled)"
              :disabled="!canManageChannels"
              @change="
                (value) => toggleChannel(record, Boolean(value))
              "
/>
</template>
</Table.Column>
        <Table.Column title="操作">
<template #default="{ record }">
<Button
              :disabled="!canManageChannels || record.channelCode === 'IN_APP'"
              @click="validateChannel(record)"
              >
验证连接
</Button>
</template>
</Table.Column>
      </Table>

      <template v-else-if="tab === 'providers'">
        <Button
          v-if="canManageChannels"
          class="toolbar"
          type="primary"
          @click="providerOpen = true"
          >
配置供应商
</Button>
        <Table
          :data-source="providers"
          row-key="providerKey"
          :pagination="false"
          >
<Table.Column data-index="channelCode" title="渠道" /><Table.Column
            data-index="displayName"
            title="供应商"
          /><Table.Column title="凭据引用">
<template #default="{ record }">
<code>{{ safeCredentialLabel(record) }}</code>
</template>
</Table.Column><Table.Column title="状态">
<template #default="{ record }">
<Tag>{{ record.enabled ? '已启用' : '未启用' }}</Tag>
</template>
</Table.Column>
</Table>
      </template>

      <template v-else-if="tab === 'templates'">
        <Button
          v-if="canManageTemplates"
          class="toolbar"
          type="primary"
          @click="templateOpen = true"
          >
新建模板版本
</Button>
        <Table
          :data-source="templates"
          row-key="versionId"
          :pagination="false"
          :scroll="{ x: 900 }"
          >
<Table.Column title="模板">
<template #default="{ record }">
{{ record.templateKey }} · V{{ record.versionNo }}
</template>
</Table.Column><Table.Column data-index="channelCode" title="渠道" /><Table.Column
            data-index="localeCode"
            title="语言"
          /><Table.Column title="状态">
<template #default="{ record }">
<Tag>{{ record.state }}</Tag>
</template>
</Table.Column><Table.Column title="操作">
<template #default="{ record }">
<Space>
<Button
                  v-if="record.state === 'DRAFT'"
                  @click="templateCommand(record, 'submit-review')"
                  >
提交审核
</Button><Button
                  v-if="record.state === 'PENDING_REVIEW'"
                  type="primary"
                  @click="templateCommand(record, 'publish')"
                  >
发布
</Button><Button
                  v-if="record.state === 'PENDING_REVIEW'"
                  danger
                  @click="templateCommand(record, 'reject')"
                  >
驳回
</Button>
</Space>
</template>
</Table.Column>
</Table>
      </template>

      <template v-else>
        <Button
          v-if="canManageChannels"
          class="toolbar"
          type="primary"
          @click="routingOpen = true"
          >
配置路由
</Button>
        <Table :data-source="policies" row-key="id" :pagination="false">
<Table.Column data-index="categoryCode" title="类别" /><Table.Column
            data-index="priorityCode"
            title="优先级"
          /><Table.Column
            data-index="orderedChannels"
            title="渠道顺序"
          /><Table.Column title="策略">
<template #default="{ record }">
<Tag v-if="record.mandatoryCategory">强制</Tag><Tag v-if="record.fallbackEnabled">允许降级</Tag>
</template>
</Table.Column>
</Table>
      </template>
    </Card>

    <Modal
      v-model:open="providerOpen"
      title="配置供应商（仅保存密钥系统引用）"
      @ok="saveProvider"
      >
<FormItem label="渠道">
<Select
          v-model:value="providerForm.channelCode"
          :options="
            channelCodes
              .filter((v) => v !== 'IN_APP')
              .map((value) => ({ label: value, value }))
          "
/>
</FormItem><FormItem label="供应商标识">
<Input v-model:value="providerForm.providerKey" />
</FormItem><FormItem label="显示名称">
<Input v-model:value="providerForm.displayName" />
</FormItem><FormItem label="凭据引用">
<Input
          v-model:value="providerForm.credentialReference"
          placeholder="例如 vault://notification/email/prod"
/>
</FormItem>
</Modal>
    <Modal
      v-model:open="templateOpen"
      title="新建不可变模板版本"
      width="720px"
      @ok="saveTemplate"
      >
<FormItem label="模板标识">
<Input v-model:value="templateForm.templateKey" />
</FormItem><FormItem label="渠道">
<Select
          v-model:value="templateForm.channelCode"
          :options="
            channelCodes.map((value) => ({ label: value, value }))
          "
/>
</FormItem><FormItem label="语言">
<Input v-model:value="templateForm.localeCode" />
</FormItem><FormItem label="标题模板">
<Input v-model:value="templateForm.subjectTemplate" />
</FormItem><FormItem label="正文">
<Textarea
          v-model:value="templateForm.bodyTemplate"
          :rows="6"
/>
</FormItem><FormItem label="变量 Schema">
<Textarea
          v-model:value="templateForm.variableSchemaJson"
          :rows="3"
          placeholder="{&quot;userName&quot;:&quot;STRING&quot;}"
/>
</FormItem>
</Modal>
    <Modal v-model:open="routingOpen" title="配置路由策略" @ok="saveRouting">
<FormItem label="类别">
<Input v-model:value="routingForm.categoryCode" />
</FormItem><FormItem label="优先级">
<Select
          v-model:value="routingForm.priorityCode"
          :options="
            ['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((value) => ({
              label: value,
              value,
            }))
          "
/>
</FormItem><FormItem label="渠道顺序">
<Select
          v-model:value="routingForm.orderedChannels"
          mode="multiple"
          :options="
            channelCodes.map((value) => ({ label: value, value }))
          "
/>
</FormItem><Space>
<Checkbox v-model:checked="routingForm.fallbackEnabled">
允许按顺序降级
</Checkbox><Checkbox v-model:checked="routingForm.mandatoryCategory">
强制类别
</Checkbox>
</Space><FormItem label="免打扰开始">
<Input
          v-model:value="routingForm.quietStart"
          placeholder="22:00"
/>
</FormItem><FormItem label="免打扰结束">
<Input
          v-model:value="routingForm.quietEnd"
          placeholder="07:00"
/>
</FormItem><FormItem label="时区">
<Input v-model:value="routingForm.zoneId" />
</FormItem>
</Modal>
  </Page>
</template>

<style scoped>
.notice,
.toolbar {
  margin-bottom: 16px;
}
code {
  word-break: break-all;
}
@media (max-width: 768px) {
  :deep(.ant-modal) {
    max-width: calc(100vw - 24px);
  }
  :deep(.ant-table) {
    overflow-x: auto;
  }
}
</style>
