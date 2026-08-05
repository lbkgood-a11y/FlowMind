<script setup lang="ts">
import type { AnnouncementDraft, AnnouncementStatistics, AnnouncementVersion } from '#/api/announcement-governance';

import { computed, h, onMounted, reactive, ref } from 'vue';
import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Button, Card, Checkbox, DatePicker, Descriptions, DescriptionsItem, Drawer, FormItem, Input, message, Modal, Select, Space, Table, Tag } from 'ant-design-vue';

import { approveAnnouncement, createAnnouncementDraft, createAnnouncementVersion, getAnnouncementStatistics, getAnnouncementVersions, rejectAnnouncement, remindAnnouncement, submitAnnouncementReview, withdrawAnnouncement } from '#/api/announcement-governance';
import { getOrgUnits } from '#/api/system/org';
import { getRoleList } from '#/api/system/role';
import { getUserList } from '#/api/system/user';

const Textarea = Input.TextArea;
const access = useAccess();
const can = (code: string) => access.hasAccessByCodes([code]);
const loading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const statisticsOpen = ref(false);
const records = ref<AnnouncementVersion[]>([]);
const statistics = ref<AnnouncementStatistics>();
const selected = ref<AnnouncementVersion>();
const predecessorId = ref<string>();
const orgOptions = ref<Array<{ label: string; value: string }>>([]);
const roleOptions = ref<Array<{ label: string; value: string }>>([]);
const userOptions = ref<Array<{ label: string; value: string }>>([]);
const query = reactive({ keyword: '', state: undefined as string | undefined });
const audienceType = ref<'ALL' | 'ORGANIZATION' | 'ROLE' | 'USER'>('ALL');
const audienceIds = ref<string[]>([]);
const includeDescendants = ref(true);
const form = reactive({
  confirmationDeadline: undefined as any,
  confirmationRequired: false,
  confirmationStatement: '',
  content: '',
  effectiveUntil: undefined as any,
  pinFrom: undefined as any,
  pinUntil: undefined as any,
  priority: 'NORMAL',
  scheduledPublishAt: undefined as any,
  title: '',
});

const audienceOptions = computed(() => audienceType.value === 'ORGANIZATION' ? orgOptions.value : audienceType.value === 'ROLE' ? roleOptions.value : userOptions.value);
const stateColor: Record<string, string> = { DRAFT: 'default', EXPIRED: 'default', PENDING_REVIEW: 'processing', PUBLISHED: 'success', REJECTED: 'error', SCHEDULED: 'warning', SUPERSEDED: 'default', WITHDRAWN: 'error' };

async function load() {
  if (!can('/api/v1/announcements:GET')) return;
  loading.value = true;
  try { records.value = (await getAnnouncementVersions({ ...query, page: 1, size: 100 })).records; }
  finally { loading.value = false; }
}

async function loadSelectors() {
  const [orgs, roles, users] = await Promise.all([getOrgUnits({ status: 1 }), getRoleList({ status: 1 }), getUserList({ page: 1, size: 100, status: 1 })]);
  orgOptions.value = orgs.map((item) => ({ label: item.unitName, value: item.id }));
  roleOptions.value = roles.map((item) => ({ label: item.roleName, value: item.id }));
  userOptions.value = users.items.map((item) => ({ label: item.username, value: item.id }));
}

function openCreate() {
  predecessorId.value = undefined;
  Object.assign(form, { confirmationDeadline: undefined, confirmationRequired: false, confirmationStatement: '', content: '', effectiveUntil: undefined, pinFrom: undefined, pinUntil: undefined, priority: 'NORMAL', scheduledPublishAt: undefined, title: '' });
  audienceType.value = 'ALL'; audienceIds.value = []; modalOpen.value = true;
  void loadSelectors();
}

function openNextVersion(item: AnnouncementVersion) {
  predecessorId.value = item.id;
  Object.assign(form, { confirmationDeadline: undefined, confirmationRequired: Boolean(item.confirmationRequired), confirmationStatement: '', content: item.content, effectiveUntil: undefined, pinFrom: undefined, pinUntil: undefined, priority: item.priority, title: item.title });
  audienceType.value = 'ALL'; audienceIds.value = []; modalOpen.value = true;
  void loadSelectors();
}

async function save() {
  if (!form.title.trim() || !form.content.trim() || (audienceType.value !== 'ALL' && audienceIds.value.length === 0)) {
    message.warning('请完整填写标题、正文和受众'); return;
  }
  saving.value = true;
  try {
    const payload: AnnouncementDraft = {
      audience: [{ includeDescendants: audienceType.value === 'ORGANIZATION' && includeDescendants.value, subjectIds: audienceType.value === 'ALL' ? [] : audienceIds.value, type: audienceType.value }],
      confirmationDeadline: form.confirmationDeadline?.toISOString(), confirmationRequired: form.confirmationRequired,
      confirmationStatement: form.confirmationRequired ? form.confirmationStatement : undefined,
      content: form.content, effectiveUntil: form.effectiveUntil?.toISOString(), pinFrom: form.pinFrom?.toISOString(), pinUntil: form.pinUntil?.toISOString(), priority: form.priority, title: form.title,
    };
    const version = predecessorId.value ? await createAnnouncementVersion(predecessorId.value, payload) : await createAnnouncementDraft(payload);
    if (form.scheduledPublishAt) { await submitAnnouncementReview(version.id); }
    modalOpen.value = false; message.success('公告草稿已创建'); await load();
  } finally { saving.value = false; }
}

async function command(action: () => Promise<unknown>, success: string) {
  await action(); message.success(success); await load();
}

function askReason(title: string, action: (reason: string) => Promise<unknown>) {
  let reason = '';
  Modal.confirm({ title, content: () => h(Input.TextArea, { 'onUpdate:value': (value: string) => { reason = value; }, placeholder: '请输入审计原因', rows: 3 }), onOk: async () => { if (!reason.trim()) throw new Error('请输入原因'); await action(reason.trim()); await load(); } });
}

function approveOnSchedule(id: string) {
  let scheduledAt: any;
  Modal.confirm({
    title: '设置定时发布时间',
    content: () => h(DatePicker, { 'onUpdate:value': (value: any) => { scheduledAt = value; }, showTime: true }),
    onOk: async () => {
      if (!scheduledAt) throw new Error('请选择发布时间');
      await approveAnnouncement(id, scheduledAt.toISOString());
      await load();
    },
  });
}

async function showStatistics(item: AnnouncementVersion) {
  selected.value = item; statistics.value = await getAnnouncementStatistics(item.id); statisticsOpen.value = true;
}

onMounted(load);
</script>

<template>
  <Page title="公告发布工作台" description="版本化起草、双人审核、定时发布与确认闭环">
    <Card>
      <Space class="toolbar" wrap>
        <Input v-model:value="query.keyword" allow-clear placeholder="搜索标题或正文" @press-enter="load" />
        <Select v-model:value="query.state" allow-clear placeholder="生命周期" :options="Object.keys(stateColor).map((value) => ({ label: value, value }))" />
        <Button @click="load">查询</Button>
        <Button v-if="can('/api/v2/announcements:POST')" type="primary" @click="openCreate">新建公告</Button>
      </Space>
      <Table class="table" :data-source="records" :loading="loading" row-key="id" :pagination="false" :scroll="{ x: 1100 }">
        <Table.Column title="公告/版本" :width="260"><template #default="{ record }"><div>{{ record.title }}</div><small>{{ record.announcementId }} · V{{ record.versionNo }}</small></template></Table.Column>
        <Table.Column data-index="priority" title="优先级" :width="90" />
        <Table.Column title="状态" :width="130"><template #default="{ record }"><Tag :color="stateColor[record.lifecycleState]">{{ record.lifecycleState }}</Tag></template></Table.Column>
        <Table.Column title="受众政策" :width="130"><template #default="{ record }">{{ record.audienceMode === 'FROZEN' ? '发布冻结' : '动态可见' }}</template></Table.Column>
        <Table.Column title="确认" :width="140"><template #default="{ record }">{{ record.confirmationRequired ? `强制 · ${record.confirmationDeadline || '无期限'}` : '无需确认' }}</template></Table.Column>
        <Table.Column title="操作" fixed="right" :width="400"><template #default="{ record }"><Space wrap>
          <Button v-if="record.lifecycleState === 'DRAFT' && can('/api/v2/announcements/*/review:POST')" size="small" @click="command(() => submitAnnouncementReview(record.id), '已提交审核')">提交审核</Button>
          <Button v-if="record.lifecycleState === 'PENDING_REVIEW' && can('/api/v2/announcements/*/review:POST')" size="small" type="primary" @click="command(() => approveAnnouncement(record.id), '审核通过并发布')">审核通过</Button>
          <Button v-if="record.lifecycleState === 'PENDING_REVIEW' && can('/api/v2/announcements/*/review:POST')" size="small" @click="approveOnSchedule(record.id)">定时批准</Button>
          <Button v-if="record.lifecycleState === 'PENDING_REVIEW'" size="small" danger @click="askReason('驳回公告', (reason) => rejectAnnouncement(record.id, reason))">驳回</Button>
          <Button v-if="record.lifecycleState === 'PUBLISHED'" size="small" @click="showStatistics(record)">统计</Button>
          <Button v-if="['PUBLISHED','WITHDRAWN','EXPIRED','SUPERSEDED'].includes(record.lifecycleState) && can('/api/v2/announcements:POST')" size="small" @click="openNextVersion(record)">新版本</Button>
          <Button v-if="record.lifecycleState === 'PUBLISHED' && record.audienceMode === 'FROZEN' && can('/api/v2/announcements/*/reminders:POST')" size="small" @click="command(() => remindAnnouncement(record.id, record.confirmationRequired ? 'UNCONFIRMED' : 'UNREAD'), '催读任务已创建')">催读</Button>
          <Button v-if="['PUBLISHED','SCHEDULED'].includes(record.lifecycleState) && can('/api/v2/announcements/*/withdraw:POST')" size="small" danger @click="askReason('撤回公告', (reason) => withdrawAnnouncement(record.id, reason))">撤回</Button>
        </Space></template></Table.Column>
      </Table>
    </Card>

    <Modal v-model:open="modalOpen" title="创建公告版本" :confirm-loading="saving" width="760px" @ok="save">
      <div class="form-grid">
        <FormItem label="标题" required><Input v-model:value="form.title" :maxlength="160" /></FormItem>
        <FormItem label="优先级"><Select v-model:value="form.priority" :options="['LOW','NORMAL','HIGH','URGENT'].map((value) => ({ label: value, value }))" /></FormItem>
        <FormItem label="受众类型" required><Select v-model:value="audienceType" :options="[{label:'全员',value:'ALL'},{label:'组织',value:'ORGANIZATION'},{label:'角色',value:'ROLE'},{label:'指定用户',value:'USER'}]" @change="audienceIds = []" /></FormItem>
        <FormItem v-if="audienceType !== 'ALL'" label="授权受众" required><Select v-model:value="audienceIds" mode="multiple" show-search :options="audienceOptions" /></FormItem>
        <FormItem v-if="audienceType === 'ORGANIZATION'"><Checkbox v-model:checked="includeDescendants">包含下级组织</Checkbox></FormItem>
        <FormItem label="有效期"><DatePicker v-model:value="form.effectiveUntil" show-time /></FormItem>
        <FormItem label="置顶开始"><DatePicker v-model:value="form.pinFrom" show-time /></FormItem>
        <FormItem label="置顶结束"><DatePicker v-model:value="form.pinUntil" show-time /></FormItem>
        <FormItem class="wide"><Checkbox v-model:checked="form.confirmationRequired">要求接收人确认（发布时冻结受众）</Checkbox></FormItem>
        <FormItem v-if="form.confirmationRequired" label="确认声明" class="wide" required><Input v-model:value="form.confirmationStatement" :maxlength="512" /></FormItem>
        <FormItem v-if="form.confirmationRequired" label="确认期限"><DatePicker v-model:value="form.confirmationDeadline" show-time /></FormItem>
        <FormItem label="正文" class="wide" required><Textarea v-model:value="form.content" :rows="8" /></FormItem>
      </div>
    </Modal>

    <Drawer v-model:open="statisticsOpen" title="发布统计" width="480">
      <Descriptions v-if="statistics" bordered :column="1">
        <DescriptionsItem label="应覆盖">{{ statistics.accountableCount }}</DescriptionsItem><DescriptionsItem label="已读">{{ statistics.readCount }}</DescriptionsItem>
        <DescriptionsItem label="已确认">{{ statistics.confirmedCount }}</DescriptionsItem><DescriptionsItem label="逾期">{{ statistics.overdueCount }}</DescriptionsItem>
      </Descriptions>
    </Drawer>
  </Page>
</template>

<style scoped>.toolbar{margin-bottom:16px}.table{margin-top:8px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:4px 16px}.wide{grid-column:1/-1}@media(max-width:760px){.form-grid{grid-template-columns:1fr}.wide{grid-column:auto}}</style>
