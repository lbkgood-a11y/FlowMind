<script setup lang="ts">
import type { SystemAuthorizationApi, SystemOrgApi, SystemRoleApi } from '#/api';

import { computed, nextTick, onMounted, provide, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';
import { Button, Empty, Input, message, Modal, Space, Tabs, Tag, Tooltip, Tree } from 'ant-design-vue';

import { getAuthorizationAdminOptions, getAuthorizationResourceTree, getOrgDimensions, getOrgTree } from '#/api';
import { deleteRole, getRoleList } from '#/api/system/role';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import { BusinessPageScaffold } from '#/shared';
import AcceptanceTab from '#/views/system/authz/components/AcceptanceTab.vue';
import DecisionPreviewTab from '#/views/system/authz/components/DecisionPreviewTab.vue';
import DiagnosticsTab from '#/views/system/authz/components/DiagnosticsTab.vue';
import FieldPoliciesTab from '#/views/system/authz/components/FieldPoliciesTab.vue';
import FunctionGrantsTab from '#/views/system/authz/components/FunctionGrantsTab.vue';
import GuardTemplatesTab from '#/views/system/authz/components/GuardTemplatesTab.vue';
import LowcodeBundlesTab from '#/views/system/authz/components/LowcodeBundlesTab.vue';
import DataPermissionList from '#/views/system/data-permission/list.vue';
import PageCapabilityAuthorizationWorkbench from '#/views/system/role/components/PageCapabilityAuthorizationWorkbench.vue';
import RoleBasicInfo from './RoleBasicInfo.vue';

const TabPane = Tabs.TabPane;
const route = useRoute();
const { hasAccessByCodes } = useAccess();

const ROLE = { create: '/api/v1/roles:POST', delete: '/api/v1/roles/*:DELETE', query: '/api/v1/roles:GET', update: '/api/v1/roles/*:PUT' };
const AUTHZ = { delete: '/api/v1/authz/**:DELETE', post: '/api/v1/authz/**:POST', put: '/api/v1/authz/**:PUT', query: '/api/v1/authz/**:GET' };
const DATA = { query: '/api/v1/data-policies:GET' };

const canQueryRoles = computed(() => hasAccessByCodes([ROLE.query]));
const canCreateRole = computed(() => hasAccessByCodes([ROLE.create]));
const canUpdateRole = computed(() => hasAccessByCodes([ROLE.update]));
const canDeleteRole = computed(() => hasAccessByCodes([ROLE.delete]));
const canQuery = computed(() => hasAccessByCodes([AUTHZ.query]));
const canCreate = computed(() => hasAccessByCodes([AUTHZ.post]));
const canUpdate = computed(() => hasAccessByCodes([AUTHZ.put]));
const canDelete = computed(() => hasAccessByCodes([AUTHZ.delete]));
const canQueryData = computed(() => hasAccessByCodes([DATA.query]));

const loading = ref(false);
const saving = ref(false);
const roleList = ref<SystemRoleApi.SystemRole[]>([]);
const roleKeyword = ref('');
const selectedRoleId = ref('');
const activeTab = ref('basic');
const basicInfoRef = ref<InstanceType<typeof RoleBasicInfo>>();
const expandedKeys = ref<Array<number | string>>(['enabled', 'disabled']);
const resourceTree = ref<SystemAuthorizationApi.ResourceTree>();
const adminOptions = ref<SystemAuthorizationApi.AdminOptions>();
const orgDimensions = ref<SystemOrgApi.OrgDimension[]>([]);
const orgOptionsMap = ref<Record<string, { label: string; value: string }[]>>({});

const selectedRole = computed(() => roleList.value.find((role) => role.id === selectedRoleId.value));
const visibleRoles = computed(() => {
  const keyword = roleKeyword.value.trim().toLowerCase();
  if (!keyword) return roleList.value;
  return roleList.value.filter((role) => `${role.roleName} ${role.roleCode}`.toLowerCase().includes(keyword));
});
const roleTreeData = computed(() => [
  { key: 'enabled', selectable: false, title: '启用角色', children: visibleRoles.value.filter((r) => (r.status ?? 1) === 1).map(roleNode) },
  { key: 'disabled', selectable: false, title: '停用角色', children: visibleRoles.value.filter((r) => r.status === 0).map(roleNode) },
]);
const resourceList = computed(() => (resourceTree.value?.groups ?? []).flatMap((group) => group.resources ?? []));
const resourceOptions = computed(() => resourceList.value.map((r) => ({ label: `${r.displayName || r.resourceCode} · ${r.resourceType}`, value: r.resourceCode })));
const fieldResourceOptions = computed(() => {
  const registered = resourceList.value.filter((resource) => (resource.fields ?? []).length > 0);
  const options = (resources: SystemAuthorizationApi.ResourceNode[]) => resources.map((resource) => ({
    label: `${resource.displayName || resource.resourceCode} · ${resource.resourceCode}`,
    value: resource.resourceCode,
  }));
  return [
    {
      label: '固定业务资源',
      options: options(registered.filter((resource) => !resource.resourceType?.startsWith('LOWCODE'))),
    },
    {
      label: '低代码资源',
      options: options(registered.filter((resource) => resource.resourceType?.startsWith('LOWCODE'))),
    },
  ].filter((group) => group.options.length > 0);
});
const roleOptions = computed(() => roleList.value.map((r) => ({ label: `${r.roleName} (${r.roleCode})`, value: r.id })));

const tabs = computed(() => [
  { key: 'basic', label: '基本信息', visible: canQueryRoles.value },
  { key: 'page', label: '页面功能', visible: canQuery.value },
  { key: 'function', label: '业务功能', visible: canQuery.value },
  { key: 'data', label: '数据权限', visible: canQueryData.value },
  { key: 'field', label: '字段规则', visible: canQuery.value },
  { key: 'lowcode', label: '低代码应用', visible: canQuery.value },
  { key: 'guard', label: '守卫规则', visible: canQuery.value },
  { key: 'preview', label: '决策预览', visible: canQuery.value },
  { key: 'diagnostics', label: '上线诊断', visible: canQuery.value },
].filter((tab) => tab.visible));
const roleScopedTabKeys = new Set(['page', 'function', 'data', 'field', 'lowcode']);

function roleNode(role: SystemRoleApi.SystemRole) {
  return { key: role.id, title: `${role.roleName} (${role.roleCode})` };
}
function findResource(code: string) { return resourceList.value.find((r) => r.resourceCode === code); }
function actionOptionsForResource(code: string) { return (findResource(code)?.actions ?? []).filter((a) => a.status !== 0).map((a) => ({ label: a.actionCode, value: a.actionCode })); }
function fieldOptionsForResource(code: string) { return (findResource(code)?.fields ?? []).filter((f) => f.status !== 0).map((f) => ({ label: f.fieldLabel || f.fieldKey, value: f.fieldKey })); }

function flattenOrgTree(list: SystemOrgApi.OrgTreeNode[]) {
  const result: SystemOrgApi.OrgTreeNode[] = [];
  const walk = (nodes: SystemOrgApi.OrgTreeNode[]) => {
    nodes.forEach((node) => {
      result.push(node);
      if (node.children?.length) walk(node.children);
    });
  };
  walk(list);
  return result;
}
async function ensureOrgOptions(dimensionCode: string) {
  if (!dimensionCode || orgOptionsMap.value[dimensionCode]) return;
  try {
    const tree = await getOrgTree(dimensionCode);
    orgOptionsMap.value = {
      ...orgOptionsMap.value,
      [dimensionCode]: flattenOrgTree(tree).map((item) => ({
        label: `${item.unitName} (${item.unitCode})`,
        value: item.id,
      })),
    };
  } catch { /* org tree not available */ }
}

function syncUrl() {
  if (typeof window === 'undefined') return;
  const url = new URL(window.location.href);
  url.searchParams.set('tab', activeTab.value);
  if (selectedRoleId.value) url.searchParams.set('roleId', selectedRoleId.value);
  else url.searchParams.delete('roleId');
  // Workbench state belongs to the current application page. Updating browser
  // history directly avoids creating another top-level application tab.
  window.history.replaceState(window.history.state, '', url);
}
function selectRole(keys: Array<number | string>) {
  selectedRoleId.value = keys[0] ? String(keys[0]) : '';
}
async function createRole() {
  activeTab.value = 'basic';
  await nextTick();
  basicInfoRef.value?.openCreate();
}
async function editRole() {
  if (!selectedRoleId.value) return;
  activeTab.value = 'basic';
  await nextTick();
  basicInfoRef.value?.openEdit();
}
async function removeRole() {
  if (!selectedRoleId.value || !selectedRole.value) return;
  Modal.confirm({
    title: `确认删除角色「${selectedRole.value.roleName}」？`,
    content: '删除后不可恢复，已授予此角色的权限将一并失效。',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteRole(selectedRoleId.value);
      message.success('角色已删除');
      selectedRoleId.value = '';
      await load();
    },
  });
}
async function load(preferredRoleId?: string) {
  loading.value = true;
  try {
    const [roles, tree, options, dims] = await Promise.all([
      canQueryRoles.value ? getRoleList() : Promise.resolve([]),
      canQuery.value ? getAuthorizationResourceTree() : Promise.resolve(undefined),
      canQuery.value ? getAuthorizationAdminOptions() : Promise.resolve(undefined),
      canQuery.value ? getOrgDimensions().catch(() => [] as SystemOrgApi.OrgDimension[]) : Promise.resolve([] as SystemOrgApi.OrgDimension[]),
    ]);
    roleList.value = roles;
    resourceTree.value = tree;
    adminOptions.value = options;
    orgDimensions.value = dims;
    const requestedRole = preferredRoleId || selectedRoleId.value || String(route.query.roleId ?? '');
    selectedRoleId.value = roles.some((r) => r.id === requestedRole) ? requestedRole : '';
    const requestedTab = String(route.query.tab ?? 'basic');
    activeTab.value = tabs.value.some((tab) => tab.key === requestedTab) ? requestedTab : (tabs.value[0]?.key ?? 'basic');
  } finally { loading.value = false; }
}
async function roleSaved(roleId: string) {
  await load(roleId);
  selectedRoleId.value = roleId;
}

watch([selectedRoleId, activeTab], syncUrl);
provide('authzContext', { selectedRoleId, resourceTree, adminOptions, roleList, resourceOptions, fieldResourceOptions, roleOptions, resourceList, orgDimensions, orgOptionsMap, ensureOrgOptions, canQuery, canCreate, canUpdate, canDelete, loading, saving, findResource, actionOptionsForResource, fieldOptionsForResource });
onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="role-auth-workbench" pattern="master-detail">
      <section class="workbench-layout">
        <aside class="role-master">
          <div class="role-master-title"><div><h3>角色</h3><span>{{ roleList.length }} 个角色</span></div><Tooltip title="刷新"><Button shape="circle" size="small" @click="() => load()"><IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" /></Button></Tooltip></div>
          <Input v-model:value="roleKeyword" allow-clear placeholder="搜索角色名称或编码" />
          <Button v-if="canCreateRole" type="primary" block @click="createRole"><Plus class="size-4" />新建角色</Button>
          <Tree v-model:expanded-keys="expandedKeys" :tree-data="roleTreeData" :selected-keys="selectedRoleId ? [selectedRoleId] : []" block-node @select="selectRole" />
        </aside>

        <section class="workbench-detail">
          <header class="detail-header"><div><h2>{{ selectedRole?.roleName || '角色与授权工作台' }}</h2><span>{{ selectedRole?.roleCode || '选择左侧角色后配置授权' }}</span></div><Space :size="8"><Tag :color="selectedRole?.status === 0 ? 'default' : 'success'">{{ selectedRole ? (selectedRole.status === 0 ? '停用' : '启用') : '未选择' }}</Tag><Button v-if="selectedRoleId && canUpdateRole" size="small" @click="editRole">编辑</Button><Button v-if="selectedRoleId && canDeleteRole" size="small" danger @click="removeRole">删除</Button></Space></header>
          <Tabs v-if="tabs.length" v-model:active-key="activeTab" class="workbench-tabs" tab-position="top">
            <TabPane v-for="tab in tabs" :key="tab.key" :tab="tab.label">
              <Empty v-if="activeTab === tab.key && roleScopedTabKeys.has(tab.key) && !selectedRoleId" class="role-required-empty" description="请先从左侧选择角色，再配置该角色的授权" />
              <RoleBasicInfo v-else-if="activeTab === 'basic' && tab.key === 'basic'" ref="basicInfoRef" :role="selectedRole" :can-create="canCreateRole" :can-update="canUpdateRole" @saved="roleSaved" />
              <PageCapabilityAuthorizationWorkbench v-else-if="activeTab === 'page' && tab.key === 'page' && selectedRoleId" :role-id="selectedRoleId" :can-manage="canUpdateRole" />
              <FunctionGrantsTab v-else-if="activeTab === 'function' && tab.key === 'function'" />
              <DataPermissionList v-else-if="activeTab === 'data' && tab.key === 'data'" embedded :external-role-id="selectedRoleId" />
              <FieldPoliciesTab v-else-if="activeTab === 'field' && tab.key === 'field'" />
              <LowcodeBundlesTab v-else-if="activeTab === 'lowcode' && tab.key === 'lowcode'" />
              <GuardTemplatesTab v-else-if="activeTab === 'guard' && tab.key === 'guard'" />
              <DecisionPreviewTab v-else-if="activeTab === 'preview' && tab.key === 'preview'" />
              <div v-else-if="activeTab === 'diagnostics' && tab.key === 'diagnostics'" class="diagnostic-stack"><AcceptanceTab /><DiagnosticsTab /></div>
            </TabPane>
          </Tabs>
          <Empty v-else description="当前账号没有可用的角色与授权功能权限" />
        </section>
      </section>
    </BusinessPageScaffold>
  </Page>
</template>

<style scoped>
.role-auth-workbench,.workbench-layout,.workbench-detail,.workbench-tabs{display:flex;min-width:0;min-height:0}.role-auth-workbench,.workbench-detail,.workbench-tabs{flex:1;flex-direction:column}.workbench-layout{flex:1;gap:12px;padding:12px;background:#f5f7fa}.role-master,.workbench-detail{overflow:hidden;background:#fff;border:1px solid #e5e7eb;border-radius:8px;box-shadow:0 1px 2px rgb(0 0 0 / 3%)}.role-master{display:flex;flex:0 0 248px;flex-direction:column;gap:10px;padding:14px}.workbench-detail{padding:0}.role-master-title,.detail-header{display:flex;align-items:flex-start;justify-content:space-between}.role-master-title{padding-bottom:10px;border-bottom:1px solid #edf0f5}.detail-header{min-height:64px;padding:14px 16px 10px;border-bottom:1px solid #edf0f5}.role-master-title h3,.detail-header h2{margin:0;font-size:15px;font-weight:700}.role-master-title span,.detail-header span{font-size:12px;color:#6b7280}.role-master :deep(.ant-tree){flex:1;min-height:0;overflow:auto;background:transparent}.workbench-tabs{overflow:hidden}.workbench-tabs :deep(.ant-tabs-nav){margin:0;padding:0 16px}.workbench-tabs :deep(.ant-tabs-content-holder),.workbench-tabs :deep(.ant-tabs-content),.workbench-tabs :deep(.ant-tabs-tabpane){height:100%;min-height:0}.workbench-tabs :deep(.ant-tabs-tabpane){overflow:hidden;background:#fff}.workbench-tabs :deep(.ant-tabs-tabpane-active>div){box-sizing:border-box;display:flex;width:100%;height:100%;min-width:0;min-height:0;flex-direction:column;padding:16px;overflow:auto}.workbench-tabs :deep(.tb-table-frame){width:100%;min-width:0;min-height:260px}.role-required-empty{display:flex;height:100%;min-height:260px;align-items:center;justify-content:center}.diagnostic-stack{display:flex;flex-direction:column;gap:16px;padding:16px}@media(max-width:840px){.workbench-layout{flex-direction:column;padding:8px}.role-master{flex:0 0 auto;max-height:260px}.detail-header{min-height:auto;gap:10px}.workbench-tabs :deep(.ant-tabs-nav){padding:0 10px}.workbench-tabs :deep(.ant-tabs-tabpane-active>div){padding:10px}}
</style>
