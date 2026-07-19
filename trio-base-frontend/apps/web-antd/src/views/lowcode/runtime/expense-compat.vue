<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { getRuntimeApplicationDescriptor } from '#/api/lowcode';

const router = useRouter();
const unavailable = ref(false);

onMounted(async () => {
  try {
    const descriptor = await getRuntimeApplicationDescriptor('expense_report');
    await router.replace({
      name: 'LowcodeRuntimeApp',
      params: { appKey: descriptor.appKey },
      query: { version: descriptor.version },
    });
  } catch {
    unavailable.value = true;
  }
});
</script>

<template>
  <Page auto-content-height>
    <div class="compat-loading">
      {{ unavailable ? '费用报销应用尚未发布，请先在低代码运行中心发布应用。' : '正在打开费用报销...' }}
    </div>
  </Page>
</template>

<style scoped>
.compat-loading {
  display: flex;
  height: 100%;
  min-height: 0;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #606b7b;
  background: #fff;
}
</style>
