<script setup lang="ts">
import type { OpenApiOperationsApi } from '#/api';
import { onMounted, ref } from 'vue';
import { Page } from '@vben/common-ui';
import { Alert, Button, Card, Col, Row, Space, Statistic, Steps, Tag } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import { getLifecycleReadiness } from '#/api';

const router = useRouter();
const loading = ref(false);
const readiness = ref<OpenApiOperationsApi.LifecycleReadiness>();
async function load() { loading.value = true; try { readiness.value = await getLifecycleReadiness(); } finally { loading.value = false; } }
onMounted(load);
</script>

<template>
  <Page title="OpenAPI 生命周期总览" description="从契约设计到应用开放、策略生效和运行监控的闭环状态">
    <Alert v-if="readiness && !readiness.publicRuntimeEnabled" type="warning" show-icon message="公共运行时保持关闭；完成审批和策略同步后仍需显式启用网关路由。" />
    <Card :loading="loading" class="section">
      <Steps :items="readiness?.stages.map(stage => ({ title: stage.title, status: stage.ready ? 'finish' : 'wait', description: stage.ready ? '已就绪' : '待完善' }))" />
      <Space class="actions" wrap>
        <Button v-for="stage in readiness?.stages" :key="stage.key" @click="router.push(stage.route)">{{ stage.title }}</Button>
      </Space>
    </Card>
    <Row :gutter="[12,12]">
      <Col v-for="(count,key) in readiness?.assetCounts" :key="key" :xs="12" :md="8" :xl="6"><Card><Statistic :title="key" :value="count" /></Card></Col>
    </Row>
    <Card class="section" title="阻断项"><Space wrap><Tag v-for="item in readiness?.blockers" :key="item" color="orange">{{ item }}</Tag><Tag v-if="readiness?.ready" color="green">生命周期资产已就绪</Tag></Space></Card>
  </Page>
</template>
<style scoped>.section{margin-bottom:12px}.actions{margin-top:20px}</style>
