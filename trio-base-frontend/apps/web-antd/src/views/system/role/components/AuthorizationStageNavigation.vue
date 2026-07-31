<script setup lang="ts">
import { Steps } from 'ant-design-vue';

type StageKey = 'data' | 'field' | 'function' | 'guard' | 'preview';

const props = defineProps<{
  activeKey: string;
  completedKeys?: string[];
}>();
const emit = defineEmits<{ change: [key: StageKey] }>();

const stages: Array<{ description: string; key: StageKey; title: string }> = [
  { key: 'function', title: '功能与菜单', description: '先授予功能，再确认菜单可见性' },
  { key: 'data', title: '数据范围', description: '限定组织与业务数据边界' },
  { key: 'field', title: '字段访问', description: '配置隐藏、脱敏与写入限制' },
  { key: 'guard', title: '业务约束', description: '查看动作自动带出的约束' },
  { key: 'preview', title: '验证', description: '用真实用户或当前角色模拟' },
];

const current = () => Math.max(0, stages.findIndex((item) => item.key === props.activeKey));
</script>

<template>
  <Steps
    class="authorization-stage-navigation"
    direction="vertical"
    size="small"
    :current="current()"
    :items="stages.map((stage) => ({
      ...stage,
      status: completedKeys?.includes(stage.key) ? 'finish' : undefined,
    }))"
    @change="(index) => emit('change', stages[index]?.key ?? 'function')"
  />
</template>

<style scoped>
.authorization-stage-navigation {
  min-width: 190px;
}
</style>
