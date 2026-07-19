<script setup lang="ts">
import type { ActionApi } from '#/api/action-client';

import { computed } from 'vue';

import { Button, Dropdown, Menu } from 'ant-design-vue';

export interface BusinessActionMenuItem {
  availability?: ActionApi.ActionCandidateValidationResult;
  danger?: boolean;
  key: string;
  label: string;
}

const props = defineProps<{
  items: BusinessActionMenuItem[];
}>();

const emit = defineEmits<{
  execute: [item: BusinessActionMenuItem];
}>();

const visibleItems = computed(() =>
  props.items.filter((item) => item.availability?.visible !== false),
);
const MenuItem = Menu.Item;
</script>

<template>
  <Dropdown v-if="visibleItems.length > 0" trigger="click">
    <Button size="small">更多</Button>
    <template #overlay>
      <Menu>
        <MenuItem
          v-for="item in visibleItems"
          :key="item.key"
          :danger="item.danger || item.availability?.danger"
          :disabled="item.availability?.enabled === false"
          @click="emit('execute', item)"
        >
          {{ item.label }}
        </MenuItem>
      </Menu>
    </template>
  </Dropdown>
</template>
