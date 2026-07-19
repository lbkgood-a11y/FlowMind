<script setup lang="ts">
import { Page } from '@vben/common-ui';

import { BusinessPageScaffold, CompactToolbar } from '#/shared';
</script>

<template>
  <Page title="部门管理" description="部门管理页面已接入 Vben 路由骨架，下一步对接 TrioBase 组织部门 API。">
    <BusinessPageScaffold class="dept-page" pattern="master-detail">
      <template #toolbar>
        <CompactToolbar title="部门管理" subtitle="部门组织能力建设中" />
      </template>
      <div class="rounded-md border border-dashed p-8 text-center text-muted-foreground">
        部门管理能力建设中
      </div>
    </BusinessPageScaffold>
  </Page>
</template>
