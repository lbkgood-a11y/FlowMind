import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'mdi:application-braces-outline',
      order: 70,
      title: $t('lowcode.title'),
    },
    name: 'LowcodeCenter',
    path: '/lowcode',
    children: [
      {
        path: 'application',
        name: 'LowcodeApplication',
        meta: {
          icon: 'mdi:application-edit-outline',
          title: '应用管理',
        },
        component: () => import('#/views/lowcode/application/list.vue'),
      },
      {
        path: 'apps',
        name: 'LowcodeAppCenter',
        meta: {
          icon: 'mdi:apps',
          title: $t('lowcode.runtime.title'),
        },
        component: () => import('#/views/lowcode/runtime/center.vue'),
      },
      {
        path: 'apps/:appKey',
        name: 'LowcodeRuntimeApp',
        meta: {
          hideInMenu: true,
          title: $t('lowcode.runtime.app'),
        },
        component: () => import('#/views/lowcode/runtime/app.vue'),
      },
      {
        path: 'form',
        name: 'LowcodeForm',
        meta: {
          icon: 'mdi:form-select',
          title: $t('lowcode.form.title'),
        },
        component: () => import('#/views/lowcode/form/list.vue'),
      },
      {
        path: 'expense',
        name: 'LowcodeExpense',
        meta: {
          icon: 'mdi:receipt-text-edit-outline',
          title: '费用报销',
        },
        component: () => import('#/views/lowcode/runtime/expense-compat.vue'),
      },
    ],
  },
];

export default routes;
