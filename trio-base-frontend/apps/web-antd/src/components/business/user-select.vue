<script lang="ts" setup>
import type { SystemUserApi } from '#/api';
import type { SelectProps } from 'ant-design-vue';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { Select, Spin } from 'ant-design-vue';

import { getUserById, getUserList } from '#/api';

type UserSelectValue = null | string | string[] | undefined;
type SelectMode = NonNullable<SelectProps['mode']>;

type UserSelectOption = {
  label: string;
  user: SystemUserApi.SystemUser;
  value: string;
};

const props = withDefaults(
  defineProps<{
    activeOnly?: boolean;
    allowClear?: boolean;
    disabled?: boolean;
    maxTagCount?: SelectProps['maxTagCount'];
    mode?: SelectMode;
    multiple?: boolean;
    pageSize?: number;
    placeholder?: string;
    searchDelay?: number;
    size?: SelectProps['size'];
    status?: SelectProps['status'];
    value?: UserSelectValue;
  }>(),
  {
    activeOnly: true,
    allowClear: true,
    disabled: false,
    multiple: false,
    pageSize: 20,
    placeholder: '请选择用户',
    searchDelay: 300,
  },
);

const emit = defineEmits<{
  change: [
    value: UserSelectValue,
    users: SystemUserApi.SystemUser[],
  ];
  'update:value': [value: UserSelectValue];
}>();

defineOptions({
  inheritAttrs: false,
  name: 'UserSelect',
});

const loading = ref(false);
const loadedOnce = ref(false);
const remoteUserIds = ref<string[]>([]);
const searchKeyword = ref('');
const userMap = ref(new Map<string, SystemUserApi.SystemUser>());
const currentRequestId = ref(0);
let searchTimer: ReturnType<typeof setTimeout> | undefined;

const resolvedMode = computed<SelectMode | undefined>(() =>
  props.mode ?? (props.multiple ? 'multiple' : undefined),
);

const selectedIds = computed(() => normalizeValue(props.value));

const selectOptions = computed<UserSelectOption[]>(() => {
  const ids = new Set<string>();
  const options: UserSelectOption[] = [];

  for (const id of [...selectedIds.value, ...remoteUserIds.value]) {
    if (ids.has(id)) {
      continue;
    }

    const user = userMap.value.get(id);
    if (!user) {
      continue;
    }

    ids.add(id);
    options.push({
      label: formatUserLabel(user),
      user,
      value: user.id,
    });
  }

  return options;
});

watch(
  () => selectedIds.value.join('|'),
  () => {
    void ensureSelectedUsers();
  },
  { immediate: true },
);

watch(
  () => [props.activeOnly, props.pageSize],
  () => {
    if (loadedOnce.value) {
      void loadUsers(searchKeyword.value);
    }
  },
);

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
});

function normalizeValue(value: UserSelectValue) {
  if (Array.isArray(value)) {
    return value.filter((id) => Boolean(id));
  }
  return value ? [value] : [];
}

function upsertUsers(users: SystemUserApi.SystemUser[]) {
  const nextMap = new Map(userMap.value);

  for (const user of users) {
    nextMap.set(user.id, user);
  }

  userMap.value = nextMap;
}

function formatUserLabel(user: SystemUserApi.SystemUser) {
  const contactText = [user.phone, user.email].filter(Boolean).join(' / ');
  const statusText = user.status === 0 ? '（禁用）' : '';

  return contactText
    ? `${user.username} · ${contactText}${statusText}`
    : `${user.username}${statusText}`;
}

function getSelectedUsers(value: UserSelectValue) {
  return normalizeValue(value)
    .map((id) => userMap.value.get(id))
    .filter((user): user is SystemUserApi.SystemUser => Boolean(user));
}

async function ensureSelectedUsers() {
  const missingIds = selectedIds.value.filter((id) => !userMap.value.has(id));

  if (missingIds.length === 0) {
    return;
  }

  const users = await Promise.all(
    missingIds.map(async (id) => {
      try {
        return await getUserById(id);
      } catch {
        return {
          id,
          status: 1,
          username: id,
        } satisfies SystemUserApi.SystemUser;
      }
    }),
  );

  upsertUsers(users);
}

async function loadUsers(keyword = '') {
  const requestId = currentRequestId.value + 1;
  currentRequestId.value = requestId;
  loading.value = true;

  try {
    const result = await getUserList({
      keyword: keyword.trim() || undefined,
      page: 1,
      size: props.pageSize,
      status: props.activeOnly ? 1 : undefined,
    });

    if (requestId !== currentRequestId.value) {
      return;
    }

    upsertUsers(result.items);
    remoteUserIds.value = result.items.map((user) => user.id);
    loadedOnce.value = true;
  } finally {
    if (requestId === currentRequestId.value) {
      loading.value = false;
    }
  }
}

function loadUsersIfNeeded() {
  if (!loadedOnce.value && !loading.value) {
    void loadUsers(searchKeyword.value);
  }
}

function handleSearch(keyword: string) {
  searchKeyword.value = keyword;

  if (searchTimer) {
    clearTimeout(searchTimer);
  }

  searchTimer = setTimeout(() => {
    void loadUsers(keyword);
  }, props.searchDelay);
}

function handleChange(value: UserSelectValue) {
  emit('update:value', value);
  emit('change', value, getSelectedUsers(value));
}

function handleDropdownVisibleChange(open: boolean) {
  if (open) {
    loadUsersIfNeeded();
  }
}
</script>

<template>
  <Select
    v-bind="$attrs"
    :allow-clear="allowClear"
    :disabled="disabled"
    :filter-option="false"
    :loading="loading"
    :max-tag-count="maxTagCount"
    :mode="resolvedMode"
    :options="selectOptions"
    :placeholder="placeholder"
    :show-search="true"
    :size="size"
    :status="status"
    :value="value"
    class="trio-user-select"
    option-filter-prop="label"
    @change="handleChange"
    @dropdown-visible-change="handleDropdownVisibleChange"
    @focus="loadUsersIfNeeded"
    @search="handleSearch"
  >
    <template v-if="loading" #notFoundContent>
      <div class="trio-user-select__loading">
        <Spin size="small" />
      </div>
    </template>
  </Select>
</template>

<style scoped>
.trio-user-select {
  width: 100%;
}

.trio-user-select__loading {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}
</style>
