import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:database-zap',
      order: 60,
      title: $t('data.title'),
    },
    name: 'DataCenter',
    path: '/data',
    children: [
      {
        path: 'catalog',
        name: 'DataCatalog',
        meta: {
          icon: 'lucide:table-properties',
          title: $t('data.catalog.title'),
        },
        component: () => import('#/views/data/hybrid-query/index.vue'),
      },
      {
        path: 'hybrid-query',
        name: 'HybridQuery',
        meta: {
          icon: 'lucide:scan-search',
          title: $t('data.hybridQuery.title'),
        },
        component: () => import('#/views/data/hybrid-query/index.vue'),
      },
    ],
  },
];

export default routes;
