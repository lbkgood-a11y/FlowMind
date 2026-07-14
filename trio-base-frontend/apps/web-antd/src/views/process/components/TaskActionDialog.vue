<script setup lang="ts">
import type { ProcessApi } from '#/api/process';

import { computed, ref, watch } from 'vue';

import { Input, Modal, Select } from 'ant-design-vue';

import {
  addSignTask,
  approveTask,
  getRejectTargets,
  rejectTask,
  transferTask,
} from '#/api/process';
import { UserSelect } from '#/components/business';

export type TaskActionType = 'ADD_SIGN' | 'APPROVE' | 'REJECT' | 'TRANSFER';

const props = defineProps<{
  action: TaskActionType;
  open: boolean;
  task?: ProcessApi.TaskItem;
}>();

const emit = defineEmits<{
  success: [action: TaskActionType, task: ProcessApi.TaskItem];
  'update:open': [open: boolean];
}>();

const Textarea = Input.TextArea;
const saving = ref(false);
const comment = ref('');
const targetNodeId = ref<string>();
const targetUserId = ref<string>();
const rejectTargets = ref<string[]>([]);

const title = computed(() => {
  const labels: Record<TaskActionType, string> = {
    ADD_SIGN: '并行加签',
    APPROVE: '通过审批',
    REJECT: '驳回审批',
    TRANSFER: '转办任务',
  };
  return labels[props.action];
});

const rejectOptions = computed(() => [
  { label: '终止流程', value: '' },
  ...rejectTargets.value.map((nodeId) => ({
    label: `退回节点 ${nodeId}`,
    value: nodeId,
  })),
]);

watch(
  () => [props.open, props.action, props.task?.id] as const,
  async ([open, action]) => {
    if (!open) return;
    comment.value = '';
    targetNodeId.value = undefined;
    targetUserId.value = undefined;
    rejectTargets.value = [];
    if (action === 'REJECT' && props.task) {
      rejectTargets.value = await getRejectTargets(props.task.processInstanceId);
    }
  },
  { immediate: true },
);

async function submit() {
  const task = props.task;
  if (!task) return;
  if ((props.action === 'TRANSFER' || props.action === 'ADD_SIGN') && !targetUserId.value) {
    return;
  }

  saving.value = true;
  try {
    const operationId = crypto.randomUUID();
    let result: ProcessApi.TaskItem;
    if (props.action === 'APPROVE') {
      result = await approveTask(task.id, {
        comment: comment.value || undefined,
        operationId,
      });
    } else if (props.action === 'REJECT') {
      result = await rejectTask(task.id, {
        comment: comment.value || undefined,
        operationId,
        targetNodeId: targetNodeId.value || undefined,
      });
    } else if (props.action === 'TRANSFER') {
      result = await transferTask(task.id, {
        newAssigneeId: targetUserId.value!,
        operationId,
      });
    } else {
      result = await addSignTask(task.id, {
        assigneeId: targetUserId.value!,
        operationId,
      });
    }
    emit('success', props.action, result);
    emit('update:open', false);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Modal
    :confirm-loading="saving"
    destroy-on-close
    :ok-button-props="{ danger: action === 'REJECT' }"
    ok-text="确认"
    :open="open"
    :title="title"
    @cancel="emit('update:open', false)"
    @ok="submit"
  >
    <p class="mb-3"><strong>任务：</strong>{{ task?.title }}</p>
    <Select
      v-if="action === 'REJECT'"
      v-model:value="targetNodeId"
      class="mb-3 w-full"
      :options="rejectOptions"
      placeholder="选择终止或退回节点"
    />
    <UserSelect
      v-if="action === 'TRANSFER' || action === 'ADD_SIGN'"
      v-model:value="targetUserId"
      class="mb-3"
      placeholder="选择启用用户"
    />
    <Textarea
      v-if="action === 'APPROVE' || action === 'REJECT'"
      v-model:value="comment"
      :rows="3"
      placeholder="处理意见（可选）"
    />
  </Modal>
</template>
