import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/ai',
    name: 'AiCenter',
    meta: {
      icon: 'lucide:bot',
      order: 50,
      title: 'AI 能力',
    },
    children: [
      {
        path: 'assistant',
        name: 'AiAssistantWorkbench',
        meta: {
          icon: 'lucide:message-square-more',
          title: 'AI 助手',
        },
        component: () => import('#/views/ai/assistant/index.vue'),
      },
    ],
  },
];

export default routes;
