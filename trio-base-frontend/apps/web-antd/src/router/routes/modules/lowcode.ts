import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:blocks',
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
          icon: 'lucide:app-window',
          title: '应用管理',
        },
        component: () => import('#/views/lowcode/application/list.vue'),
      },
      {
        path: 'apps',
        name: 'LowcodeAppCenter',
        meta: {
          icon: 'lucide:layout-grid',
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
          icon: 'lucide:list-checks',
          title: $t('lowcode.form.title'),
        },
        component: () => import('#/views/lowcode/form/list.vue'),
      },
    ],
  },
];

export default routes;
