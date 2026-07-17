import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/openapi-operations',
    name: 'OpenApiOperations',
    meta: {
      icon: 'mdi:connection',
      order: 9997,
      title: 'OpenAPI 集成运维',
    },
    children: [
      {
        path: 'overview',
        name: 'OpenApiLifecycleOverview',
        meta: {
          icon: 'mdi:monitor-dashboard',
          title: '生命周期总览',
        },
        component: () => import('#/views/openapi/lifecycle/overview.vue'),
      },
      ...['structures','mappings','value-maps','connectors','routes','orchestrations','callbacks','products','applications','subscriptions','policies'].map((path) => ({
        path,
        name: `OpenApiLifecycle_${path.replaceAll('-', '_')}`,
        meta: { title: path },
        component: () => import('#/views/openapi/lifecycle/resource.vue'),
      })),
      { path: 'executions', name: 'OpenApiExecutions', meta: { title: '执行中心' }, component: () => import('#/views/openapi/operations/executions.vue') },
      { path: 'quarantine', name: 'OpenApiCallbackQuarantine', meta: { title: '回调隔离区' }, component: () => import('#/views/openapi/operations/quarantine.vue') },
    ],
  },
];

export default routes;
