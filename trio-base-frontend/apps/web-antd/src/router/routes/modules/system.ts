import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'ion:settings-outline',
      order: 9997,
      title: $t('system.title'),
    },
    name: 'System',
    path: '/system',
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        meta: {
          icon: 'mdi:user',
          title: $t('system.user.title'),
        },
        component: () => import('#/views/system/user/list.vue'),
      },
      {
        path: 'role',
        name: 'SystemRole',
        meta: {
          icon: 'mdi:account-group',
          title: $t('system.role.title'),
        },
        component: () => import('#/views/system/role/list.vue'),
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        meta: {
          icon: 'mdi:menu',
          title: $t('system.menu.title'),
        },
        component: () => import('#/views/system/menu/list.vue'),
      },
      {
        path: 'org',
        name: 'SystemOrg',
        meta: {
          icon: 'charm:organisation',
          title: $t('system.org.title'),
        },
        component: () => import('#/views/system/org/list.vue'),
      },
      {
        path: 'data-permission',
        name: 'SystemDataPermission',
        meta: {
          icon: 'mdi:shield-key-outline',
          title: $t('system.dataPermission.title'),
        },
        component: () => import('#/views/system/data-permission/list.vue'),
      },
      {
        path: 'audit-log',
        name: 'SystemAuditLog',
        meta: {
          icon: 'mdi:clipboard-text-clock-outline',
          title: $t('system.auditLog.title'),
        },
        component: () => import('#/views/system/audit-log/list.vue'),
      },
      {
        path: 'session',
        name: 'SystemSession',
        meta: {
          icon: 'mdi:account-clock-outline',
          title: $t('system.session.title'),
        },
        component: () => import('#/views/system/session/list.vue'),
      },
      {
        path: 'dictionary',
        name: 'SystemDictionary',
        meta: {
          icon: 'mdi:book-open-variant-outline',
          title: $t('system.dictionary.title'),
        },
        component: () => import('#/views/system/dictionary/list.vue'),
      },
      {
        path: 'config',
        name: 'SystemConfig',
        meta: {
          icon: 'mdi:tune-variant',
          title: $t('system.config.title'),
        },
        component: () => import('#/views/system/config/list.vue'),
      },
    ],
  },
];

export default routes;
