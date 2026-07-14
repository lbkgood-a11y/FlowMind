<script setup lang="ts">
import type { ProcessApi } from '#/api/process';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  RadioButton,
  RadioGroup,
  Select,
  Space,
  Tag,
} from 'ant-design-vue';

import {
  createProcessPackage,
  getBusinessObjectCatalog,
  getBusinessObjectCatalogList,
} from '#/api/process';

import FlowDesigner from '../components/FlowDesigner.vue';
import {
  businessActionDisplayName,
  buildBusinessClosureProcessDefinition,
  businessClosureCompletionChecks,
  businessClosureValidationIssues,
  buildSafeCondition,
  catalogFormFields,
  participantDisplayName,
  validateProcessDefinition,
  type BusinessClosureDesignerConfig,
  type ParticipantType,
} from '../components/process-designer';

const loadingCatalog = ref(false);
const saving = ref(false);
const technicalPreviewOpen = ref(false);
const businessObjects = ref<ProcessApi.BusinessObjectSummary[]>([]);
const catalog = ref<ProcessApi.BusinessObjectCatalog>();
const flowJson = ref('');

const config = reactive<BusinessClosureDesignerConfig>({
  agentActionCode: undefined,
  allowedStatuses: [],
  approvedEventCode: undefined,
  approvedStatus: '',
  approveActionCode: undefined,
  businessRefContextKey: 'businessId',
  businessRefFieldKey: undefined,
  businessRefFixedValue: undefined,
  businessRefSourceType: 'API_INPUT',
  conditionFieldKey: undefined,
  conditionOperator: 'TRUE',
  conditionValue: undefined,
  createActionCode: undefined,
  launchMode: 'EXISTING_DOCUMENT',
  name: '',
  notifyActionCode: undefined,
  participantDimensionCode: 'ADMIN',
  participantType: 'ROLE',
  participantValue: 'DEPT_HEAD',
  processKey: '',
  rejectedEventCode: undefined,
  rejectedStatus: '',
  retryClosureActionCode: undefined,
  selectedFormKey: undefined,
  startStatus: '',
  submitActionCode: undefined,
  updateStatusActionCode: '',
  viewActionCode: undefined,
});

const businessObjectOptions = computed(() =>
  businessObjects.value.map((item) => ({
    label: `${item.displayName} (${item.typeCode})`,
    value: item.typeCode,
  })),
);

const statusOptions = computed(() =>
  (catalog.value?.statuses ?? []).map((item) => ({
    label: `${item.displayName} (${item.statusCode})`,
    value: item.statusCode,
  })),
);

const formOptions = computed(() =>
  (catalog.value?.forms ?? []).map((item) => ({
    label: `${item.displayName} (${item.formRole})`,
    value: item.formKey,
  })),
);

const formFieldOptions = computed(() =>
  catalogFormFields(catalog.value).map((item) => ({
    label: `${item.label} (${item.key})`,
    value: item.key,
  })),
);

const businessRefSourceOptions = [
  { label: 'API 输入', value: 'API_INPUT' },
  { label: '单据页上下文', value: 'PAGE_CONTEXT' },
  { label: '表单字段', value: 'FORM_FIELD' },
  { label: '流程上下文', value: 'PROCESS_CONTEXT' },
  { label: '固定值', value: 'FIXED' },
];

const conditionOperatorOptions = [
  { label: '默认通过', value: 'TRUE' },
  { label: '等于', value: 'EQ' },
  { label: '不等于', value: 'NE' },
  { label: '大于', value: 'GT' },
  { label: '大于等于', value: 'GTE' },
  { label: '小于', value: 'LT' },
  { label: '小于等于', value: 'LTE' },
];

const participantCatalog = [
  { label: '部门负责人', type: 'ROLE' as ParticipantType, value: 'DEPT_HEAD' },
  { label: '财务审批人', type: 'ROLE' as ParticipantType, value: 'FINANCE_APPROVER' },
  { label: '行政部门', type: 'DEPT' as ParticipantType, value: 'ADMIN_DEPT' },
  { label: '财务部门', type: 'DEPT' as ParticipantType, value: 'FINANCE_DEPT' },
  { label: '张三', type: 'USER' as ParticipantType, value: 'USER_ZHANGSAN' },
  { label: '李四', type: 'USER' as ParticipantType, value: 'USER_LISI' },
];

const participantOptions = computed(() =>
  participantCatalog
    .filter((item) => item.type === config.participantType)
    .map((item) => ({
      label: `${item.label} (${item.value})`,
      value: item.value,
    })),
);

const permissionOptions = computed(() =>
  (catalog.value?.permissions ?? []).map((item) => ({
    label: `${item.displayName} (${item.actionCode})`,
    value: item.actionCode,
  })),
);

const updateStatusActionOptions = computed(() =>
  actionOptionsByType('UPDATE_STATUS'),
);

const createActionOptions = computed(() =>
  actionOptionsByType('CREATE_DOCUMENT'),
);

const notifyActionOptions = computed(() =>
  actionOptionsByType('NOTIFICATION'),
);

const eventOptions = computed(() =>
  (catalog.value?.events ?? []).map((item) => ({
    label: `${item.displayName} (${item.eventCode})`,
    value: item.eventCode,
  })),
);

const agentOptions = computed(() => [
  { label: '不触发', value: '' },
  ...(catalog.value?.agentActions ?? []).map((item) => ({
    label: `${item.displayName} (${item.agentActionCode})`,
    value: item.agentActionCode,
  })),
]);

const completionChecks = computed(() =>
  businessClosureCompletionChecks(catalog.value, config),
);

const validationIssues = computed(() =>
  businessClosureValidationIssues(catalog.value, config),
);

const builtCondition = computed(() =>
  buildSafeCondition(
    config.conditionFieldKey,
    config.conditionOperator,
    config.conditionValue,
  ),
);

const generatedJson = computed(() => {
  if (!catalog.value) return '';
  return buildBusinessClosureProcessDefinition(catalog.value, config, flowJson.value);
});

function actionOptionsByType(actionType: string) {
  return (catalog.value?.actions ?? [])
    .filter((item) => item.actionType === actionType)
    .map((item) => ({
      label: `${item.displayName} (${item.actionCode})`,
      value: item.actionCode,
    }));
}

async function loadBusinessObjects() {
  loadingCatalog.value = true;
  try {
    businessObjects.value = await getBusinessObjectCatalogList();
    if (!catalog.value && businessObjects.value[0]) {
      await handleBusinessObjectChange(businessObjects.value[0].typeCode);
    }
  } finally {
    loadingCatalog.value = false;
  }
}

async function handleBusinessObjectChange(typeCode: string) {
  catalog.value = await getBusinessObjectCatalog(typeCode);
  applyCatalogDefaults(catalog.value);
}

function applyCatalogDefaults(nextCatalog: ProcessApi.BusinessObjectCatalog) {
  const object = nextCatalog.object;
  const action = (code: string) =>
    nextCatalog.permissions.find((item) => item.actionCode === code)?.actionCode;
  const updateStatus = nextCatalog.actions.find(
    (item) => item.actionType === 'UPDATE_STATUS',
  );
  const createDocument = nextCatalog.actions.find(
    (item) => item.actionType === 'CREATE_DOCUMENT',
  );
  const notify = nextCatalog.actions.find(
    (item) => item.actionType === 'NOTIFICATION',
  );
  const approvedEvent = nextCatalog.events.find((item) =>
    item.eventCode.toLowerCase().includes('approved'),
  );
  const rejectedEvent = nextCatalog.events.find((item) =>
    item.eventCode.toLowerCase().includes('rejected'),
  );
  const initialStatuses = nextCatalog.statuses
    .filter((item) => item.initial || ['DRAFT', 'REJECTED'].includes(item.statusCode))
    .map((item) => item.statusCode);

  config.processKey = object.typeCode;
  config.name = `${object.displayName}审批流程`;
  config.selectedFormKey =
    nextCatalog.forms.find((item) => item.formRole === 'START')?.formKey ??
    nextCatalog.forms[0]?.formKey;
  config.allowedStatuses = initialStatuses.length > 0
    ? initialStatuses
    : nextCatalog.statuses.slice(0, 1).map((item) => item.statusCode);
  config.startStatus =
    nextCatalog.statuses.find((item) => item.statusCode === 'IN_APPROVAL')?.statusCode ??
    nextCatalog.statuses[0]?.statusCode ??
    '';
  config.approvedStatus =
    nextCatalog.statuses.find((item) => item.statusCode === 'APPROVED')?.statusCode ??
    nextCatalog.statuses.at(-1)?.statusCode ??
    '';
  config.rejectedStatus =
    nextCatalog.statuses.find((item) => item.statusCode === 'REJECTED')?.statusCode ??
    nextCatalog.statuses.at(-1)?.statusCode ??
    '';
  config.submitActionCode = action('submit');
  config.viewActionCode = action('view');
  config.approveActionCode = action('approve');
  config.retryClosureActionCode = action('retryClosure');
  config.updateStatusActionCode = updateStatus?.actionCode ?? '';
  config.createActionCode = createDocument?.actionCode;
  config.notifyActionCode = notify?.actionCode;
  config.approvedEventCode = approvedEvent?.eventCode;
  config.rejectedEventCode = rejectedEvent?.eventCode;
  config.agentActionCode = undefined;
  const fields = catalogFormFields(nextCatalog);
  config.businessRefSourceType = 'API_INPUT';
  config.businessRefContextKey = 'businessId';
  config.businessRefFieldKey =
    fields.find((item) => /businessId|expenseReportId|id/i.test(item.key))?.key ??
    fields[0]?.key;
  config.businessRefFixedValue = undefined;
  config.conditionFieldKey =
    fields.find((item) => item.key === 'amount')?.key ??
    fields.find((item) => item.type === 'number' || item.type === 'integer')?.key ??
    fields[0]?.key;
  config.conditionOperator = config.conditionFieldKey ? 'GT' : 'TRUE';
  config.conditionValue = config.conditionFieldKey ? '5000' : undefined;
  config.participantType = 'ROLE';
  config.participantValue = 'DEPT_HEAD';
  config.participantDimensionCode = 'ADMIN';
}

function handleFlowChange(json: string) {
  flowJson.value = json;
}

function statusName(statusCode?: string) {
  if (!statusCode) return '-';
  return catalog.value?.statuses.find((item) => item.statusCode === statusCode)?.displayName
    ?? statusCode;
}

function actionName(code?: string) {
  return businessActionDisplayName(catalog.value, code);
}

function participantName() {
  return participantDisplayName(
    config.participantType,
    config.participantValue,
    participantCatalog,
  );
}

function handleBusinessRefSourceChange() {
  if (
    config.businessRefSourceType === 'PAGE_CONTEXT'
    || config.businessRefSourceType === 'PROCESS_CONTEXT'
  ) {
    config.businessRefContextKey ||= 'businessId';
  }
  if (config.businessRefSourceType === 'FORM_FIELD' && !config.businessRefFieldKey) {
    config.businessRefFieldKey = formFieldOptions.value[0]?.value;
  }
}

async function handleSaveAsPackage() {
  if (!catalog.value || !generatedJson.value) {
    message.warning('请选择业务对象');
    return;
  }
  const incomplete = completionChecks.value.find((item) => !item.ok);
  if (incomplete) {
    message.warning(`${incomplete.label}未完成`);
    return;
  }
  const validationErrors = validateProcessDefinition(generatedJson.value);
  if (validationErrors.length > 0) {
    message.error(validationErrors[0]);
    return;
  }
  saving.value = true;
  try {
    await createProcessPackage({
      category: 'business',
      name: config.name.trim(),
      processJson: generatedJson.value,
      processKey: config.processKey.trim(),
    });
    message.success('流程包已保存');
  } finally {
    saving.value = false;
  }
}

onMounted(loadBusinessObjects);
</script>

<template>
  <Page auto-content-height>
    <div class="business-designer">
      <div class="designer-header">
        <div>
          <h2>流程设计器</h2>
          <div class="designer-subtitle">{{ catalog?.object.displayName || '业务对象' }}</div>
        </div>
        <Space>
          <Button @click="technicalPreviewOpen = true">技术预览</Button>
          <Button :loading="saving" type="primary" @click="handleSaveAsPackage">保存流程包</Button>
        </Space>
      </div>

      <div class="designer-grid">
        <section class="config-panel">
          <Form layout="vertical" size="small">
            <div class="config-section">
              <div class="section-title">业务对象</div>
              <FormItem label="对象">
                <Select
                  :loading="loadingCatalog"
                  :options="businessObjectOptions"
                  :value="catalog?.object.typeCode"
                  placeholder="选择业务对象"
                  @change="(value) => handleBusinessObjectChange(String(value))"
                />
              </FormItem>
              <FormItem label="流程标识">
                <Input v-model:value="config.processKey" />
              </FormItem>
              <FormItem label="流程名称">
                <Input v-model:value="config.name" />
              </FormItem>
              <FormItem label="发起表单">
                <Select v-model:value="config.selectedFormKey" :options="formOptions" />
              </FormItem>
              <FormItem label="业务编号来源">
                <Select
                  v-model:value="config.businessRefSourceType"
                  :options="businessRefSourceOptions"
                  @change="handleBusinessRefSourceChange"
                />
              </FormItem>
              <FormItem v-if="config.businessRefSourceType === 'FORM_FIELD'" label="业务编号字段">
                <Select
                  v-model:value="config.businessRefFieldKey"
                  :options="formFieldOptions"
                  option-filter-prop="label"
                  show-search
                />
              </FormItem>
              <FormItem
                v-if="config.businessRefSourceType === 'PAGE_CONTEXT' || config.businessRefSourceType === 'PROCESS_CONTEXT'"
                label="上下文键"
              >
                <Input v-model:value="config.businessRefContextKey" />
              </FormItem>
              <FormItem v-if="config.businessRefSourceType === 'FIXED'" label="固定业务编号">
                <Input v-model:value="config.businessRefFixedValue" />
              </FormItem>
            </div>

            <div class="config-section">
              <div class="section-title">发起</div>
              <FormItem label="模式">
                <RadioGroup v-model:value="config.launchMode" button-style="solid">
                  <RadioButton value="EXISTING_DOCUMENT">已有单据</RadioButton>
                  <RadioButton value="CREATE_AND_LAUNCH">新建并发起</RadioButton>
                </RadioGroup>
              </FormItem>
              <FormItem v-if="config.launchMode === 'CREATE_AND_LAUNCH'" label="创建动作">
                <Select v-model:value="config.createActionCode" :options="createActionOptions" />
              </FormItem>
              <FormItem label="允许状态">
                <Select
                  v-model:value="config.allowedStatuses"
                  mode="multiple"
                  :options="statusOptions"
                />
              </FormItem>
              <FormItem label="发起后状态">
                <Select v-model:value="config.startStatus" :options="statusOptions" />
              </FormItem>
            </div>

            <div class="config-section">
              <div class="section-title">权限</div>
              <FormItem label="提交">
                <Select v-model:value="config.submitActionCode" :options="permissionOptions" />
              </FormItem>
              <FormItem label="查看">
                <Select v-model:value="config.viewActionCode" :options="permissionOptions" />
              </FormItem>
              <FormItem label="审批">
                <Select v-model:value="config.approveActionCode" :options="permissionOptions" />
              </FormItem>
              <FormItem label="重试闭环">
                <Select v-model:value="config.retryClosureActionCode" :options="permissionOptions" />
              </FormItem>
            </div>

            <div class="config-section">
              <div class="section-title">流程图配置</div>
              <FormItem label="办理人类型">
                <RadioGroup v-model:value="config.participantType" button-style="solid">
                  <RadioButton value="ROLE">角色</RadioButton>
                  <RadioButton value="DEPT">部门</RadioButton>
                  <RadioButton value="USER">用户</RadioButton>
                </RadioGroup>
              </FormItem>
              <FormItem label="办理人">
                <Select
                  v-model:value="config.participantValue"
                  :options="participantOptions"
                  option-filter-prop="label"
                  show-search
                />
              </FormItem>
              <FormItem v-if="config.participantType === 'DEPT'" label="组织维度">
                <Input v-model:value="config.participantDimensionCode" />
              </FormItem>
              <FormItem label="条件字段">
                <Select
                  v-model:value="config.conditionFieldKey"
                  allow-clear
                  :options="formFieldOptions"
                  option-filter-prop="label"
                  show-search
                />
              </FormItem>
              <FormItem label="条件关系">
                <Select v-model:value="config.conditionOperator" :options="conditionOperatorOptions" />
              </FormItem>
              <FormItem v-if="config.conditionOperator !== 'TRUE'" label="条件值">
                <Input v-model:value="config.conditionValue" />
              </FormItem>
            </div>

            <div class="config-section">
              <div class="section-title">闭环</div>
              <FormItem label="状态动作">
                <Select v-model:value="config.updateStatusActionCode" :options="updateStatusActionOptions" />
              </FormItem>
              <FormItem label="通过状态">
                <Select v-model:value="config.approvedStatus" :options="statusOptions" />
              </FormItem>
              <FormItem label="驳回状态">
                <Select v-model:value="config.rejectedStatus" :options="statusOptions" />
              </FormItem>
              <FormItem label="通知动作">
                <Select v-model:value="config.notifyActionCode" allow-clear :options="notifyActionOptions" />
              </FormItem>
              <FormItem label="通过事件">
                <Select v-model:value="config.approvedEventCode" allow-clear :options="eventOptions" />
              </FormItem>
              <FormItem label="驳回事件">
                <Select v-model:value="config.rejectedEventCode" allow-clear :options="eventOptions" />
              </FormItem>
              <FormItem label="Agent follow-up">
                <Select v-model:value="config.agentActionCode" :options="agentOptions" />
              </FormItem>
            </div>
          </Form>
        </section>

        <section class="canvas-panel">
          <FlowDesigner @change="handleFlowChange" />
        </section>

        <section class="preview-panel">
          <div class="viz-block">
            <div class="section-title">单据状态图</div>
            <div class="status-chain">
              <Tag v-for="status in config.allowedStatuses" :key="status">{{ statusName(status) }}</Tag>
              <span class="chain-arrow">→</span>
              <Tag color="processing">{{ statusName(config.startStatus) }}</Tag>
              <span class="chain-arrow">→</span>
              <Tag color="success">{{ statusName(config.approvedStatus) }}</Tag>
              <Tag color="error">{{ statusName(config.rejectedStatus) }}</Tag>
            </div>
          </div>

          <div class="viz-block">
            <div class="section-title">闭环动作链</div>
            <div class="action-chain">
              <div>APPROVED · {{ actionName(config.updateStatusActionCode) }} → {{ actionName(config.approvedEventCode) }} → {{ actionName(config.notifyActionCode) }} → {{ actionName(config.agentActionCode) }}</div>
              <div>REJECTED · {{ actionName(config.updateStatusActionCode) }} → {{ actionName(config.rejectedEventCode) }} → {{ actionName(config.notifyActionCode) }}</div>
            </div>
          </div>

          <div class="viz-block">
            <div class="section-title">流程图摘要</div>
            <div class="permission-grid">
              <span>办理人</span><strong>{{ participantName() }}</strong>
              <span>条件</span><strong>{{ builtCondition || '-' }}</strong>
            </div>
          </div>

          <div class="viz-block">
            <div class="section-title">权限矩阵</div>
            <div class="permission-grid">
              <span>发起</span><strong>{{ actionName(config.submitActionCode) }}</strong>
              <span>查看</span><strong>{{ actionName(config.viewActionCode) }}</strong>
              <span>办理</span><strong>{{ actionName(config.approveActionCode) }}</strong>
              <span>重试</span><strong>{{ actionName(config.retryClosureActionCode) }}</strong>
              <span>Agent</span><strong>{{ config.agentActionCode ? 'agentFollowUp' : '-' }}</strong>
            </div>
          </div>

          <div class="viz-block">
            <div class="section-title">完成度</div>
            <div class="check-list">
              <div
                v-for="item in completionChecks"
                :key="item.key"
                class="check-row"
              >
                <Tag :color="item.ok ? 'success' : 'warning'">
                  {{ item.label }}
                </Tag>
                <span>{{ item.location }} · {{ item.message }}</span>
              </div>
            </div>
            <div v-if="validationIssues.length" class="issue-list">
              <div v-for="issue in validationIssues" :key="`${issue.key}:${issue.location}`">
                {{ issue.location }} · {{ issue.message }}
              </div>
            </div>
          </div>

          <Empty v-if="!catalog" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
        </section>
      </div>
    </div>

    <Modal v-model:open="technicalPreviewOpen" :footer="null" title="技术预览" width="840">
      <pre class="json-preview">{{ generatedJson }}</pre>
    </Modal>
  </Page>
</template>

<style scoped>
.business-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 10px;
  padding: 12px;
}

.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
}

.designer-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.designer-subtitle {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.designer-grid {
  display: grid;
  grid-template-columns: 300px minmax(520px, 1fr) 340px;
  gap: 10px;
  min-height: 0;
  flex: 1;
}

.config-panel,
.canvas-panel,
.preview-panel {
  min-height: 0;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.config-panel,
.preview-panel {
  overflow: auto;
  padding: 12px;
}

.canvas-panel {
  overflow: hidden;
}

.config-section + .config-section,
.viz-block + .viz-block {
  margin-top: 14px;
}

.section-title {
  margin-bottom: 8px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.status-chain {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.check-list,
.issue-list {
  display: grid;
  gap: 6px;
}

.check-row {
  display: grid;
  grid-template-columns: 82px 1fr;
  gap: 6px;
  align-items: center;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.issue-list {
  margin-top: 8px;
  color: #b45309;
  font-size: 12px;
  line-height: 1.5;
}

.chain-arrow {
  color: #94a3b8;
}

.action-chain {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 12px;
  line-height: 1.6;
}

.permission-grid {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 6px 10px;
  font-size: 12px;
}

.permission-grid span {
  color: #64748b;
}

.permission-grid strong {
  color: #111827;
  font-weight: 500;
}

.json-preview {
  max-height: 560px;
  overflow: auto;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 1280px) {
  .designer-grid {
    grid-template-columns: 280px minmax(420px, 1fr);
  }

  .preview-panel {
    grid-column: 1 / -1;
  }
}
</style>
