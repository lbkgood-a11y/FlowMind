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
      {
        path: 'expense',
        name: 'LowcodeExpense',
        meta: {
          icon: 'mdi:receipt-text-edit-outline',
          title: '费用报销',
        },
        component: () => import('#/views/lowcode/expense/list.vue'),
      },
    ],
  },
];

export default routes;
