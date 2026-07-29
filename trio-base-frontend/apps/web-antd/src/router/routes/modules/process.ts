import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:workflow',
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
          icon: 'lucide:files',
          title: $t('process.package.title'),
        },
        component: () => import('#/views/process/package/list.vue'),
      },
      {
        path: 'instance',
        name: 'ProcessInstance',
        meta: {
          icon: 'lucide:circle-play',
          title: $t('process.instance.title'),
        },
        component: () => import('#/views/process/instance/list.vue'),
      },
      {
        path: 'task',
        name: 'TaskCenter',
        meta: {
          icon: 'lucide:clipboard-check',
          title: $t('process.task.title'),
        },
        component: () => import('#/views/process/task/list.vue'),
      },
      {
        path: 'designer',
        name: 'ProcessDesigner',
        meta: {
          hideInMenu: true,
          icon: 'lucide:git-branch-plus',
          title: $t('process.designer.title'),
        },
        component: () => import('#/views/process/designer/index.vue'),
      },
    ],
  },
];

export default routes;
