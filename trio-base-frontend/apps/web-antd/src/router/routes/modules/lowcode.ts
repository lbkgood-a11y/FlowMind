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
        path: 'form',
        name: 'LowcodeForm',
        meta: {
          icon: 'mdi:form-select',
          title: $t('lowcode.form.title'),
        },
        component: () => import('#/views/lowcode/form/list.vue'),
      },
    ],
  },
];

export default routes;
