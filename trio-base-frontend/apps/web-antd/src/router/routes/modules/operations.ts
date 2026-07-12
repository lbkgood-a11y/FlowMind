import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'mdi:application-cog-outline',
      order: 9998,
      title: $t('operations.title'),
    },
    name: 'Operations',
    path: '/operations',
    children: [
      {
        path: 'announcement',
        name: 'OperationsAnnouncement',
        meta: {
          icon: 'mdi:bullhorn-outline',
          title: $t('operations.announcement.title'),
        },
        component: () => import('#/views/operations/announcement/list.vue'),
      },
      {
        path: 'message',
        name: 'OperationsMessage',
        meta: {
          icon: 'mdi:message-badge-outline',
          title: $t('operations.message.title'),
        },
        component: () => import('#/views/operations/message/list.vue'),
      },
      {
        path: 'file',
        name: 'OperationsFile',
        meta: {
          icon: 'mdi:file-cog-outline',
          title: $t('operations.file.title'),
        },
        component: () => import('#/views/operations/file/list.vue'),
      },
      {
        path: 'import-export',
        name: 'OperationsImportExport',
        meta: {
          icon: 'mdi:database-arrow-up-outline',
          title: $t('operations.importExport.title'),
        },
        component: () => import('#/views/operations/import-export/list.vue'),
      },
      {
        path: 'job',
        name: 'OperationsJob',
        meta: {
          icon: 'mdi:timer-cog-outline',
          title: $t('operations.job.title'),
        },
        component: () => import('#/views/operations/job/list.vue'),
      },
    ],
  },
];

export default routes;
