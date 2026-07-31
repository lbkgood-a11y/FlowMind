<script setup lang="ts">
import { Space, Tag } from 'ant-design-vue';

defineProps<{
  readHide?: boolean;
  readMask?: boolean;
  writeDeny?: boolean;
}>();
</script>

<template>
  <Space :size="4">
    <Tag :color="readHide ? 'success' : 'default'">读取隐藏</Tag>
    <Tag :color="readMask ? 'success' : 'default'">读取脱敏</Tag>
    <Tag :color="writeDeny ? 'success' : 'default'">写入拒绝</Tag>
  </Space>
</template>
