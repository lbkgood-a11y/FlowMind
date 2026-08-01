import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:settings',
      order: 9997,
      title: $t('system.title'),
    },
    name: 'System',
    path: '/system',
    children: [
      {
        path: 'tenant',
        name: 'SystemTenant',
        meta: {
          icon: 'lucide:building-2',
          title: $t('system.tenant.title'),
        },
        component: () => import('#/views/system/tenant/list.vue'),
      },
      {
        path: 'user',
        name: 'SystemUser',
        meta: {
          icon: 'lucide:user',
          title: $t('system.user.title'),
        },
        component: () => import('#/views/system/user/list.vue'),
      },
      {
        path: 'role-workbench',
        name: 'SystemRoleAuthorizationWorkbench',
        meta: {
          icon: 'lucide:shield-user',
          title: '角色与授权',
        },
        component: () => import('#/views/system/role-workbench/index.vue'),
      },
      {
        path: 'role',
        name: 'SystemRole',
        redirect: { name: 'SystemRoleAuthorizationWorkbench', query: { tab: 'basic' } },
        meta: {
          hideInMenu: true,
          title: $t('system.role.title'),
        },
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        meta: {
          icon: 'lucide:list-tree',
          title: $t('system.menu.title'),
        },
        component: () => import('#/views/system/menu/list.vue'),
      },
      {
        path: 'authorization-governance',
        name: 'SystemAuthorizationGovernance',
        meta: {
          icon: 'lucide:shield-check',
          title: '权限治理',
        },
        children: [
          {
            path: '/system/capability-catalog',
            name: 'SystemPageCapabilityCatalog',
            meta: {
              icon: 'lucide:panels-top-left',
              title: '页面能力目录',
            },
            component: () => import('#/views/system/capability-catalog/list.vue'),
          },
          {
            path: '/system/authorization-resource-catalog',
            name: 'SystemAuthorizationResourceCatalog',
            meta: {
              icon: 'lucide:boxes',
              title: '资源注册中心',
            },
            component: () =>
              import('#/views/system/authorization-resource-catalog/list.vue'),
          },
        ],
      },
      {
        path: 'org',
        name: 'SystemOrg',
        meta: {
          icon: 'lucide:network',
          title: $t('system.org.title'),
        },
        component: () => import('#/views/system/org/list.vue'),
      },
      {
        path: 'data-permission',
        name: 'SystemDataPermission',
        redirect: { name: 'SystemRoleAuthorizationWorkbench', query: { tab: 'data' } },
        meta: {
          hideInMenu: true,
          title: $t('system.dataPermission.title'),
        },
      },
      {
        path: 'audit-log',
        name: 'SystemAuditLog',
        meta: {
          icon: 'lucide:clipboard-clock',
          title: $t('system.auditLog.title'),
        },
        component: () => import('#/views/system/audit-log/list.vue'),
      },
      {
        path: 'session',
        name: 'SystemSession',
        meta: {
          icon: 'lucide:user-round-clock',
          title: $t('system.session.title'),
        },
        component: () => import('#/views/system/session/list.vue'),
      },
      {
        path: 'dictionary',
        name: 'SystemDictionary',
        meta: {
          icon: 'lucide:book-open-text',
          title: $t('system.dictionary.title'),
        },
        component: () => import('#/views/system/dictionary/list.vue'),
      },
      {
        path: 'config',
        name: 'SystemConfig',
        meta: {
          icon: 'lucide:sliders-horizontal',
          title: $t('system.config.title'),
        },
        component: () => import('#/views/system/config/list.vue'),
      },
      {
        path: 'authorization',
        name: 'SystemAuthorizationRedirect',
        redirect: { name: 'SystemRoleAuthorizationWorkbench', query: { tab: 'function' } },
        meta: {
          hideInMenu: true,
          title: '企业授权',
        },
      },
      {
        path: 'authz',
        name: 'SystemAuthz',
        redirect: { name: 'SystemRoleAuthorizationWorkbench', query: { tab: 'function' } },
        meta: {
          hideInMenu: true,
          title: '企业授权',
        },
      },
    ],
  },
];

export default routes;
