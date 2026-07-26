import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/ai',
    name: 'AiCenter',
    meta: {
      icon: 'mdi:robot-outline',
      order: 50,
      title: 'AI 能力',
    },
    children: [
      {
        path: 'assistant',
        name: 'AiAssistantWorkbench',
        meta: {
          icon: 'mdi:robot-happy-outline',
          title: 'AI 助手',
        },
        component: () => import('#/views/ai/assistant/index.vue'),
      },
    ],
  },
];

export default routes;
