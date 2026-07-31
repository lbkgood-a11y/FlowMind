<script setup lang="ts">
import type { SystemAuthorizationApi } from '#/api';

import { computed, onMounted, provide, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import { Select, Tabs } from 'ant-design-vue';

import { getAuthorizationAdminOptions, getAuthorizationResourceTree } from '#/api';
import { getRoleList } from '#/api/system/role';
import { BusinessPageScaffold, CompactToolbar } from '#/shared';

import AcceptanceTab from './components/AcceptanceTab.vue';
import DecisionPreviewTab from './components/DecisionPreviewTab.vue';
import DiagnosticsTab from './components/DiagnosticsTab.vue';
import FieldPoliciesTab from './components/FieldPoliciesTab.vue';
import FunctionGrantsTab from './components/FunctionGrantsTab.vue';
import GuardTemplatesTab from './components/GuardTemplatesTab.vue';

const TabPane = Tabs.TabPane;

const ROLE_PERMISSIONS = {
  query: '/api/v1/roles:GET',
} as const;

const AUTHZ_PERMISSIONS = {
  delete: '/api/v1/authz/**:DELETE',
  post: '/api/v1/authz/**:POST',
  put: '/api/v1/authz/**:PUT',
  query: '/api/v1/authz/**:GET',
} as const;

const { hasAccessByCodes } = useAccess();
const canQueryRoles = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.query]));
const canQuery = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.post]));
const canUpdate = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.put]));
const canDelete = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.delete]));

// ─── Shared state ───
const loading = ref(false);
const saving = ref(false);
const resourceTree = ref<SystemAuthorizationApi.ResourceTree>();
const adminOptions = ref<SystemAuthorizationApi.AdminOptions>();
const roleList = ref<{ id: string; roleName: string; roleCode: string }[]>([]);
const selectedRoleId = ref<string>('');

const resourceGroups = computed(() => resourceTree.value?.groups ?? []);
const resourceList = computed(() =>
  resourceGroups.value.flatMap((group) => group.resources ?? []),
);

const resourceOptions = computed(() =>
  resourceList.value.map((r) => ({
    label: `${r.displayName || r.resourceCode} · ${r.resourceType}`,
    value: r.resourceCode,
  })),
);

const fieldResourceOptions = computed(() =>
  resourceList.value
    .filter((r) => (r.fields ?? []).length > 0)
    .map((r) => ({
      label: `${r.displayName || r.resourceCode} · ${r.resourceType}`,
      value: r.resourceCode,
    })),
);

const roleOptions = computed(() =>
  roleList.value.map((r) => ({
    label: `${r.roleName} (${r.roleCode})`,
    value: r.id,
  })),
);

function findResource(resourceCode: string) {
  return resourceList.value.find((r) => r.resourceCode === resourceCode);
}

function actionOptionsForResource(resourceCode: string) {
  const r = findResource(resourceCode);
  return (r?.actions ?? [])
    .filter((a) => a.status !== 0)
    .map((a) => ({
      label: `${a.actionCode}${a.description ? ` · ${a.description}` : ''}`,
      value: a.actionCode,
    }));
}

function fieldOptionsForResource(resourceCode: string) {
  const r = findResource(resourceCode);
  return (r?.fields ?? [])
    .filter((f) => f.status !== 0)
    .map((f) => ({
      label: f.fieldLabel || f.fieldKey,
      value: f.fieldKey,
    }));
}

async function loadRoles() {
  if (!canQueryRoles.value) return;
  try {
    roleList.value = await getRoleList();
  } catch {
    roleList.value = [];
  }
}

async function loadResources() {
  if (!canQuery.value) return;
  loading.value = true;
  try {
    const [tree, options] = await Promise.all([
      getAuthorizationResourceTree(),
      getAuthorizationAdminOptions(),
    ]);
    resourceTree.value = tree;
    adminOptions.value = options;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadResources();
  loadRoles();
});

// Provide shared context to child tabs
provide('authzContext', {
  selectedRoleId,
  resourceTree,
  adminOptions,
  roleList,
  resourceOptions,
  fieldResourceOptions,
  roleOptions,
  resourceList,
  canQuery,
  canCreate,
  canUpdate,
  canDelete,
  loading,
  saving,
  findResource,
  actionOptionsForResource,
  fieldOptionsForResource,
});
</script>

<template>
  <Page>
    <BusinessPageScaffold class="authz-page" pattern="master-detail">
      <template #toolbar>
        <CompactToolbar title="企业授权" subtitle="统一管理功能授权、字段策略、守卫模板和决策预览" />
      </template>

      <div v-if="canQueryRoles" class="mb-3 flex items-center gap-3">
        <span class="text-sm font-medium text-gray-700">管理角色：</span>
        <Select
          v-model:value="selectedRoleId"
          :options="roleOptions"
          placeholder="选择角色以查看其授权配置"
          style="width: 260px"
          allow-clear
        />
      </div>

      <Tabs default-active-key="function">
        <TabPane key="function" tab="功能权限">
          <FunctionGrantsTab />
        </TabPane>
        <TabPane key="field" tab="字段规则">
          <FieldPoliciesTab />
        </TabPane>
        <TabPane key="guard" tab="守卫模板">
          <GuardTemplatesTab />
        </TabPane>
        <TabPane key="preview" tab="决策预览">
          <DecisionPreviewTab />
        </TabPane>
        <TabPane key="acceptance" tab="上线验收">
          <AcceptanceTab />
        </TabPane>
        <TabPane key="mapping" tab="页面功能映射诊断">
          <DiagnosticsTab />
        </TabPane>
      </Tabs>
    </BusinessPageScaffold>
  </Page>
</template>
