import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'mdi:timeline-text-outline',
      order: 80,
      title: $t('process.title'),
    },
    name: 'Process',
    path: '/process',
    children: [
      {
        path: 'package',
        name: 'ProcessPackage',
        meta: {
          icon: 'mdi:file-document-multiple-outline',
          title: $t('process.package.title'),
        },
        component: () => import('#/views/process/package/list.vue'),
      },
      {
        path: 'instance',
        name: 'ProcessInstance',
        meta: {
          icon: 'mdi:play-circle-outline',
          title: $t('process.instance.title'),
        },
        component: () => import('#/views/process/instance/list.vue'),
      },
      {
        path: 'task',
        name: 'TaskCenter',
        meta: {
          icon: 'mdi:clipboard-check-outline',
          title: $t('process.task.title'),
        },
        component: () => import('#/views/process/task/list.vue'),
      },
      {
        path: 'designer',
        name: 'ProcessDesigner',
        meta: {
          icon: 'mdi:vector-polyline-edit',
          title: $t('process.designer.title'),
        },
        component: () => import('#/views/process/designer/index.vue'),
      },
    ],
  },
];

export default routes;
