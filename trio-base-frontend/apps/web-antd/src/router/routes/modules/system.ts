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
        path: 'role',
        name: 'SystemRole',
        meta: {
          icon: 'lucide:users',
          title: $t('system.role.title'),
        },
        component: () => import('#/views/system/role/list.vue'),
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
        path: 'capability-catalog',
        name: 'SystemPageCapabilityCatalog',
        meta: {
          icon: 'lucide:panels-top-left',
          title: '能力目录',
        },
        component: () => import('#/views/system/capability-catalog/list.vue'),
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
        meta: {
          icon: 'lucide:shield-keyhole',
          title: $t('system.dataPermission.title'),
        },
        component: () => import('#/views/system/data-permission/list.vue'),
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
        redirect: '/system/authz',
        meta: {
          hideInMenu: true,
          title: '企业授权',
        },
      },
      {
        path: 'authz',
        name: 'SystemAuthz',
        meta: {
          icon: 'lucide:shield-user',
          title: '企业授权',
        },
        component: () => import('#/views/system/authz/index.vue'),
      },
    ],
  },
];

export default routes;
