<script setup lang="ts">
import type { SystemAuthorizationApi } from '#/api';

import { inject, reactive, ref } from 'vue';

import {
  Button,
  Form,
  FormItem,
  Input,
  message,
  Select,
  Table,
  Tag,
} from 'ant-design-vue';

import { previewAuthorizationDecision } from '#/api';

const ctx = inject<any>('authzContext')!;

const previewForm = reactive({
  resourceCode: '',
  actionCode: '',
  userId: '',
  businessObjectId: '',
  tenantId: 'default',
});
const previewResult = ref<null | SystemAuthorizationApi.DecisionPreview>(null);
const previewLoading = ref(false);

function asStringValue(value: unknown) {
  return typeof value === 'string' ? value : String(value ?? '');
}

function changePreviewResource(value: unknown) {
  previewForm.resourceCode = asStringValue(value);
  previewForm.actionCode = '';
  previewResult.value = null;
}

async function handlePreview() {
  if (!previewForm.resourceCode || !previewForm.actionCode || !previewForm.userId) {
    message.warning('请填写完整的决策预览参数');
    return;
  }
  previewLoading.value = true;
  previewResult.value = null;
  try {
    previewResult.value = await previewAuthorizationDecision({
      resourceCode: previewForm.resourceCode,
      actionCode: previewForm.actionCode,
      userId: previewForm.userId,
      businessObjectId: previewForm.businessObjectId || undefined,
      tenantId: previewForm.tenantId,
    });
  } catch {
    message.error('决策预览请求失败');
  } finally {
    previewLoading.value = false;
  }
}

function readModeColor(mode?: string) {
  if (mode === 'VISIBLE') return 'success';
  if (mode === 'MASKED') return 'warning';
  return 'error';
}
</script>

<template>
  <div class="max-w-3xl">
    <div class="mb-4">
      <span class="text-muted-foreground text-sm">模拟授权引擎决策，查看指定用户的资源访问权限</span>
    </div>
    <Form layout="inline" class="flex flex-wrap gap-3">
      <FormItem label="资源">
        <Select
          v-model:value="previewForm.resourceCode"
          :options="ctx.resourceOptions.value"
          style="width: 260px"
          placeholder="选择资源"
          @change="changePreviewResource"
        />
      </FormItem>
      <FormItem label="动作">
        <Select
          v-model:value="previewForm.actionCode"
          :options="ctx.actionOptionsForResource(previewForm.resourceCode)"
          style="width: 160px"
          placeholder="选择动作"
        />
      </FormItem>
      <FormItem label="用户ID">
        <Input v-model:value="previewForm.userId" placeholder="输入用户ID" style="width: 180px" />
      </FormItem>
      <FormItem label="业务对象ID">
        <Input v-model:value="previewForm.businessObjectId" placeholder="选填" style="width: 160px" />
      </FormItem>
      <FormItem>
        <Button type="primary" :loading="previewLoading" @click="handlePreview">执行预览</Button>
      </FormItem>
    </Form>

    <div v-if="previewResult" class="mt-6 rounded-lg border bg-gray-50 p-4 dark:bg-gray-900">
      <h4 class="mb-3 text-base font-semibold">决策结果</h4>
      <div class="grid grid-cols-2 gap-y-2 text-sm">
        <div>
          <span class="text-muted-foreground">结果:</span>
          <Tag :color="previewResult.allowed ? 'success' : 'error'" class="ml-2">
            {{ previewResult.allowed ? 'ALLOW' : 'DENY' }}
          </Tag>
        </div>
        <div>
          <span class="text-muted-foreground">效果:</span>
          {{ previewResult.effect || '-' }}
        </div>
        <div>
          <span class="text-muted-foreground">匹配授权ID:</span>
          {{ previewResult.matchedGrantId || '-' }}
        </div>
        <div>
          <span class="text-muted-foreground">数据范围:</span>
          {{ previewResult.dataScope?.scopeTypes?.join(', ') || '-' }}
        </div>
      </div>

      <div v-if="previewResult.reasons?.length" class="mt-3">
        <h5 class="mb-1 font-medium">决策原因:</h5>
        <ul class="list-disc space-y-1 pl-5 text-sm">
          <li v-for="(r, i) in previewResult.reasons" :key="i">
            <Tag v-if="r.code" class="mr-1">{{ r.code }}</Tag>
            {{ r.message || r.source || '-' }}
          </li>
        </ul>
      </div>

      <div v-if="previewResult.fieldRules?.length" class="mt-3">
        <h5 class="mb-1 font-medium">字段规则:</h5>
        <Table
          :columns="[
            { title: '字段', dataIndex: 'fieldKey', key: 'fieldKey' },
            { title: '读取', dataIndex: 'readMode', key: 'readMode' },
            { title: '写入', dataIndex: 'writeMode', key: 'writeMode' },
            { title: '脱敏', dataIndex: 'maskStrategy', key: 'maskStrategy' },
          ]"
          :data-source="previewResult.fieldRules"
          :pagination="false"
          size="small"
          row-key="fieldKey"
        >
          <template #bodyCell="{ column, record }: any">
            <template v-if="column.key === 'readMode'">
              <Tag :color="readModeColor(record.readMode)">{{ record.readMode }}</Tag>
            </template>
          </template>
        </Table>
      </div>

      <div v-if="previewResult.guardRequirements?.length" class="mt-3">
        <h5 class="mb-1 font-medium">守卫需求:</h5>
        <ul class="list-disc space-y-1 pl-5 text-sm">
          <li v-for="(g, i) in previewResult.guardRequirements" :key="i">
            {{ g.guardCode }} — {{ g.description || '' }}
            <Tag>{{ g.ownerService }}</Tag>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
