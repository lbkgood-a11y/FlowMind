import type { Page, Route } from '@playwright/test';

import { expect, test } from '@playwright/test';

const FORM_SCHEMA = JSON.stringify({
  additionalProperties: false,
  properties: {
    amount: { minimum: 0.01, title: '报销金额', type: 'number' },
    reason: { minLength: 2, title: '报销事由', type: 'string' },
    dept: { minLength: 1, title: '所属部门', type: 'string' },
    remark: { title: '备注', type: 'string' },
  },
  required: ['amount', 'reason', 'dept'],
  type: 'object',
});

const FORM_UI_SCHEMA = JSON.stringify({
  amount: { 'ui:widget': 'money' },
  reason: { 'ui:widget': 'textarea' },
  remark: { 'ui:widget': 'textarea' },
});

const PROCESS_PACKAGE = {
  category: 'approval',
  createdAt: '2026-07-13T09:00:00',
  description: '员工费用报销审批流程',
  formSchema: FORM_SCHEMA,
  formUiSchema: FORM_UI_SCHEMA,
  id: 'PKG001',
  name: '费用报销',
  processJson: JSON.stringify({ processKey: 'expense_report' }),
  processKey: 'expense_report',
  publishedAt: '2026-07-13T09:00:00',
  status: 'PUBLISHED',
  updatedAt: '2026-07-13T09:00:00',
  version: 1,
};

const BASE_INSTANCE = {
  completedAt: undefined,
  createdAt: '2026-07-13T09:01:00',
  currentNodeId: 'dept_approve',
  formData: JSON.stringify({ amount: 3000, dept: 'TECH', reason: '差旅报销' }),
  id: 'instance-1',
  initiatorId: 'U001',
  initiatorName: 'Admin',
  processKey: 'expense_report',
  processName: '费用报销',
  processPackageId: 'PKG001',
  startedAt: '2026-07-13T09:01:00',
  status: 'RUNNING',
  title: 'E2E 3000元报销',
  version: 1,
};

const BASE_TASK = {
  assigneeName: '部门主管',
  assigneeType: 'ROLE',
  createdAt: '2026-07-13T09:01:01',
  id: 'task-dept-1',
  nodeId: 'dept_approve',
  nodeName: '部门审批',
  nodeType: 'APPROVAL',
  nodeVisitNo: 1,
  processInstanceId: 'instance-1',
  processKey: 'expense_report',
  processName: '费用报销',
  status: 'PENDING',
  title: '费用报销 - 部门审批',
};

test('login, start a dynamic expense form, approve it, and inspect history', async ({
  page,
}) => {
  const state = {
    approved: false,
    started: false,
    startPayload: undefined as Record<string, any> | undefined,
  };
  await installApiFixtures(page, state);

  await page.goto('/auth/login');
  await login(page);
  await expect(page).toHaveURL(/\/process\/instance$/);
  await page.locator('.ant-notification').waitFor({ state: 'hidden' });

  await page.getByRole('button', { name: '发起流程' }).click();
  const startDrawer = page.locator('.ant-drawer').filter({ hasText: '发起流程' });
  await formField(startDrawer, '选择流程').locator('.ant-select-selector').click();
  await page
    .getByText('费用报销 (expense_report) · v1', { exact: true })
    .click();
  await startDrawer.getByPlaceholder('不填则自动生成').fill('E2E 3000元报销');

  const dynamicForm = startDrawer.locator('.dynamic-process-form');
  await formField(dynamicForm, '报销金额').locator('input').fill('3000');
  await formField(dynamicForm, '报销事由').locator('textarea').fill('差旅报销');
  await formField(dynamicForm, '所属部门').locator('input').fill('TECH');
  await formField(dynamicForm, '备注').locator('textarea').fill('Playwright smoke');
  await startDrawer.getByRole('button', { name: /发\s*起/ }).click();

  await expect(page.getByText('流程已发起')).toBeVisible();
  await expect(page.getByText('E2E 3000元报销')).toBeVisible();
  expect(state.startPayload).toMatchObject({
    formData: {
      amount: 3000,
      dept: 'TECH',
      reason: '差旅报销',
    },
    processKey: 'expense_report',
    processPackageId: 'PKG001',
    version: 1,
  });

  await page.locator('a[href="/process/task"]').click();
  await expect(page.getByText('费用报销 - 部门审批')).toBeVisible();
  await page.getByRole('button', { name: /通\s*过/ }).click();
  const actionDialog = page.getByRole('dialog', { name: '通过审批' });
  await actionDialog.getByPlaceholder('处理意见（可选）').fill('同意报销');
  await actionDialog.getByRole('button', { name: /确\s*认/ }).click();
  await expect(page.getByText('任务已通过')).toBeVisible();

  await page.locator('a[href="/process/instance"]').click();
  const instanceRow = page.locator('tr').filter({ hasText: 'E2E 3000元报销' });
  await expect(instanceRow.getByText('已完成')).toBeVisible();
  await instanceRow.getByRole('button', { name: /详\s*情/ }).click();

  const detailDrawer = page.locator('.ant-drawer').filter({ hasText: '流程实例详情' });
  await expect(detailDrawer.getByText('COMPLETED', { exact: true })).toBeVisible();
  await expect(detailDrawer.getByText(/部门审批 · 第 1 次 · COMPLETED/)).toBeVisible();
  await expect(detailDrawer.getByText('APPROVE', { exact: true })).toBeVisible();
  await expect(detailDrawer.getByText('财务审批')).toHaveCount(0);
});

function formField(container: ReturnType<Page['locator']>, label: string) {
  return container.locator('.ant-form-item').filter({ hasText: label });
}

async function login(page: Page) {
  await page.locator('input[name="username"]').fill('admin');
  await page.locator('input[name="password"]').fill('admin123');

  const captcha = page.locator('div[name="captcha"]');
  const action = page.locator('div[name="captcha-action"]');
  const captchaBox = await captcha.boundingBox();
  const actionBox = await action.boundingBox();
  if (!captchaBox || !actionBox) throw new Error('Login captcha is unavailable');

  await page.mouse.move(
    actionBox.x + actionBox.width / 2,
    actionBox.y + actionBox.height / 2,
  );
  await page.mouse.down();
  await page.mouse.move(
    captchaBox.x + captchaBox.width - actionBox.width / 2,
    actionBox.y + actionBox.height / 2,
    { steps: 20 },
  );
  await page.mouse.up();
  await page.getByRole('button', { name: 'login', exact: true }).click();
}

async function installApiFixtures(
  page: Page,
  state: {
    approved: boolean;
    started: boolean;
    startPayload?: Record<string, any>;
  },
) {
  await page.route('http://127.0.0.1:5556/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace(/^\/api/, '');
    const method = request.method();

    if (path === '/auth/login' && method === 'POST') {
      return ok(route, {
        accessToken: 'process-e2e-token',
        refreshToken: 'process-e2e-refresh',
      });
    }
    if (path === '/auth/me' && method === 'GET') {
      return ok(route, {
        homePath: '/process/instance',
        id: 'U001',
        realName: 'Admin',
        roles: ['ADMIN'],
        username: 'admin',
      });
    }
    if (path === '/auth/codes' && method === 'GET') {
      return ok(route, [
        '/api/v1/process-instances:GET',
        '/api/v1/process-instances/start:POST',
        '/api/v1/process-instances/*/history:GET',
        '/api/v1/tasks/my-pending:GET',
        '/api/v1/tasks/my-completed:GET',
        '/api/v1/tasks/*/approve:POST',
      ]);
    }
    if (path === '/menu/all' && method === 'GET') {
      return ok(route, processMenus());
    }
    if (path === '/process-packages' && method === 'GET') {
      return ok(route, { records: [PROCESS_PACKAGE], total: 1 });
    }
    if (path === '/process-instances/start' && method === 'POST') {
      state.startPayload = request.postDataJSON();
      state.started = true;
      return ok(route, currentInstance(state));
    }
    if (path === '/process-instances' && method === 'GET') {
      return ok(route, {
        records: state.started ? [currentInstance(state)] : [],
        total: state.started ? 1 : 0,
      });
    }
    if (path === '/tasks/my-pending' && method === 'GET') {
      return ok(route, {
        records: state.started && !state.approved ? [BASE_TASK] : [],
        total: state.started && !state.approved ? 1 : 0,
      });
    }
    if (path === '/tasks/my-completed' && method === 'GET') {
      return ok(route, {
        records: state.approved ? [{ ...BASE_TASK, status: 'APPROVED' }] : [],
        total: state.approved ? 1 : 0,
      });
    }
    if (path === '/tasks/task-dept-1/approve' && method === 'POST') {
      state.approved = true;
      return ok(route, {
        ...BASE_TASK,
        comment: '同意报销',
        completedAt: '2026-07-13T09:02:00',
        status: 'APPROVED',
      });
    }
    if (path === '/process-instances/instance-1/history' && method === 'GET') {
      return ok(route, {
        nodes: [
          historyNode('start', '开始', 'START', '2026-07-13T09:01:00'),
          historyNode(
            'dept_approve',
            '部门审批',
            'APPROVAL',
            '2026-07-13T09:01:01',
          ),
          historyNode('end', '结束', 'END', '2026-07-13T09:02:01'),
        ],
        operations: [
          {
            action: 'APPROVE',
            comment: '同意报销',
            createdAt: '2026-07-13T09:02:00',
            operationId: 'operation-e2e',
            operatorId: 'U004',
            operatorName: '部门主管',
            sourceTaskId: 'task-dept-1',
            status: 'COMPLETED',
            traceId: 'trace-process-e2e',
          },
        ],
      });
    }

    return route.fulfill({
      body: JSON.stringify({ code: 40400, message: `UNMOCKED_API:${method}:${path}` }),
      contentType: 'application/json',
      status: 404,
    });
  });
}

function currentInstance(state: { approved: boolean }) {
  return state.approved
    ? {
        ...BASE_INSTANCE,
        completedAt: '2026-07-13T09:02:01',
        currentNodeId: 'end',
        status: 'COMPLETED',
      }
    : BASE_INSTANCE;
}

function historyNode(
  nodeId: string,
  nodeName: string,
  nodeType: string,
  enteredAt: string,
) {
  return {
    enteredAt,
    exitedAt: enteredAt,
    id: `history-${nodeId}`,
    nodeId,
    nodeName,
    nodeType,
    status: 'COMPLETED',
    visitNo: 1,
  };
}

function processMenus() {
  return [
    {
      children: [
        {
          authCode: '/api/v1/process-instances:GET',
          component: '/process/instance/list',
          meta: { order: 10, title: '流程实例' },
          name: 'ProcessInstance',
          path: '/process/instance',
          type: 'menu',
        },
        {
          authCode: '/api/v1/tasks/my-pending:GET',
          component: '/process/task/list',
          meta: { order: 20, title: '任务中心' },
          name: 'TaskCenter',
          path: '/process/task',
          type: 'menu',
        },
      ],
      meta: { icon: 'mdi:timeline-text-outline', order: 80, title: '流程中心' },
      name: 'Process',
      path: '/process',
      redirect: '/process/instance',
      type: 'catalog',
    },
  ];
}

function ok(route: Route, data: unknown) {
  return route.fulfill({
    body: JSON.stringify({ code: 0, data, message: 'success' }),
    contentType: 'application/json',
    status: 200,
  });
}
