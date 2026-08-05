import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:settings-2',
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
          icon: 'lucide:megaphone',
          title: $t('operations.announcement.title'),
        },
        component: () => import('#/views/operations/announcement/list.vue'),
      },
      {
        path: 'inbox',
        name: 'OperationsInbox',
        meta: {
          icon: 'lucide:inbox',
          title: '消息中心',
        },
        component: () => import('#/views/operations/inbox/index.vue'),
      },
      {
        path: 'notification-configuration',
        name: 'OperationsNotificationConfiguration',
        meta: { icon: 'lucide:send-cog', title: '通知渠道配置' },
        component: () => import('#/views/operations/notification-configuration/index.vue'),
      },
      {
        path: 'message',
        name: 'OperationsMessage',
        meta: {
          authority: ['/api/v1/messages:GET'],
          icon: 'lucide:message-square-dot',
          title: $t('operations.message.title'),
        },
        component: () => import('#/views/operations/message/list.vue'),
      },
      {
        path: 'file',
        name: 'OperationsFile',
        meta: {
          icon: 'lucide:file-cog',
          title: $t('operations.file.title'),
        },
        component: () => import('#/views/operations/file/list.vue'),
      },
      {
        path: 'import-export',
        name: 'OperationsImportExport',
        meta: {
          icon: 'lucide:database-backup',
          title: $t('operations.importExport.title'),
        },
        component: () => import('#/views/operations/import-export/list.vue'),
      },
      {
        path: 'job',
        name: 'OperationsJob',
        meta: {
          icon: 'lucide:timer-reset',
          title: $t('operations.job.title'),
        },
        component: () => import('#/views/operations/job/list.vue'),
      },
    ],
  },
];

export default routes;
