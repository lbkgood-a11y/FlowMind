<script lang="ts" setup>
import { AuthPageLayout } from '@vben/layouts';

import { $t } from '#/locales';

const logo = '/brand/triobase-logo.png';
</script>

<template>
  <AuthPageLayout
    :page-description="$t('authentication.pageDesc')"
    :page-title="$t('authentication.pageTitle')"
  >
    <template #logo>
      <div class="absolute top-0 left-0 z-10 mt-4 ml-4 sm:mt-6 sm:ml-6">
        <img alt="TrioBase" :src="logo" class="h-10 w-auto sm:h-12" />
      </div>
    </template>
    <!-- 自定义工具栏 -->
    <!-- <template #toolbar></template> -->
  </AuthPageLayout>
</template>
