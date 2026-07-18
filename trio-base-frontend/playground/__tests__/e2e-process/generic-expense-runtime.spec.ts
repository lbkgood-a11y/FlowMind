import type { Page, Route } from '@playwright/test';

import { expect, test } from '@playwright/test';

const SCHEMA_JSON = JSON.stringify({
  additionalProperties: false,
  properties: {
    amount: { minimum: 0.01, title: '报销金额', type: 'number' },
    dept: { minLength: 1, title: '所属部门', type: 'string' },
    reason: { minLength: 2, title: '报销事由', type: 'string' },
    remark: { title: '备注', type: 'string' },
  },
  required: ['amount', 'reason', 'dept'],
  title: '费用报销',
  type: 'object',
});

const UI_SCHEMA_JSON = JSON.stringify({
  amount: { 'ui:widget': 'money' },
  reason: { 'ui:widget': 'textarea' },
  remark: { 'ui:widget': 'textarea' },
});

const DESCRIPTOR = {
  actions: [
    {
      actionCode: 'submitAndLaunch',
      actionType: 'SUBMIT_AND_LAUNCH_WORKFLOW',
      id: 'LC_APPA_EXPENSE_SUBMIT',
      label: '提交并启动审批',
      processKey: 'expense_report',
      sortOrder: 10,
      status: 'ENABLED',
    },
  ],
  appKey: 'expense_report',
  description: '费用报销通用快速应用',
  formKey: 'expense',
  formVersion: 1,
  name: '费用报销',
  pages: [
    {
      id: 'LIST',
      metadataJson: JSON.stringify({
        columns: [
          { fieldKey: 'amount', format: 'money', label: '报销金额' },
          { fieldKey: 'reason', label: '报销事由' },
          { fieldKey: 'dept', label: '所属部门' },
        ],
      }),
      pageType: 'LIST',
    },
    {
      id: 'DETAIL',
      metadataJson: JSON.stringify({
        sections: [
          {
            fields: [
              { fieldKey: 'amount', format: 'money', label: '报销金额' },
              { fieldKey: 'reason', label: '报销事由' },
              { fieldKey: 'dept', label: '所属部门' },
              { fieldKey: 'remark', label: '备注' },
            ],
            title: '单据信息',
          },
        ],
      }),
      pageType: 'DETAIL',
    },
  ],
  primaryFormDefinitionId: 'LC_FORM_EXPENSE_001',
  publishedAt: '2026-07-18T20:00:00',
  schemaJson: SCHEMA_JSON,
  tenantId: 'GLOBAL',
  uiSchemaJson: UI_SCHEMA_JSON,
  version: 1,
  versionId: 'LC_APPV_EXPENSE_REPORT_001',
};

const TASK = {
  assigneeName: '部门主管',
  assigneeType: 'ROLE',
  createdAt: '2026-07-18T20:01:00',
  id: 'task-expense-runtime',
  nodeId: 'dept_approve',
  nodeName: '部门审批',
  nodeType: 'APPROVAL',
  nodeVisitNo: 1,
  processInstanceId: 'PROC-RUNTIME-001',
  processKey: 'expense_report',
  processName: '费用报销',
  status: 'PENDING',
  title: '费用报销 - 部门审批',
};

test('generic expense runtime submit, retry workflow, approve, and show closed state', async ({
  page,
}) => {
  const state = {
    approved: false,
    retried: false,
    submitted: false,
    submittedData: {} as Record<string, unknown>,
  };
  await installApiFixtures(page, state);

  await page.goto('/auth/login');
  await login(page);
  await expect(page).toHaveURL(/\/lowcode\/apps\/expense_report\?version=1$/);
  await expect(page.getByText('费用报销通用快速应用')).toBeVisible();

  await page.getByRole('button', { name: '提交并启动审批' }).click();
  const drawer = page.locator('.ant-drawer').filter({ hasText: '提交并启动审批' });
  await formField(drawer, '报销金额').locator('input').fill('1280');
  await formField(drawer, '报销事由').locator('textarea').fill('客户拜访');
  await formField(drawer, '所属部门').locator('input').fill('销售部');
  await formField(drawer, '备注').locator('textarea').fill('generic runtime smoke');
  await drawer.getByRole('button', { name: /提\s*交/ }).click();

  await expect(page.getByText('表单已保存，流程启动待重试')).toBeVisible();
  await expect(page.getByText('待启动流程')).toBeVisible();
  expect(state.submittedData).toMatchObject({
    amount: 1280,
    dept: '销售部',
    reason: '客户拜访',
  });

  await page.getByRole('button', { name: '重试流程' }).click();
  await expect(page.getByText('流程重试已提交')).toBeVisible();
  await expect(page.getByText('审批中')).toBeVisible();

  await page.goto('/process/task');
  await expect(page.getByText('费用报销 - 部门审批')).toBeVisible();
  await page.getByRole('button', { name: /通\s*过/ }).click();
  const actionDialog = page.getByRole('dialog', { name: '通过审批' });
  await actionDialog.getByPlaceholder('处理意见（可选）').fill('同意');
  await actionDialog.getByRole('button', { name: /确\s*认/ }).click();
  await expect(page.getByText('任务已通过')).toBeVisible();

  await page.goto('/lowcode/apps/expense_report?version=1');
  await expect(page.getByText('已完成')).toBeVisible();
  await page.getByRole('button', { name: '详情' }).click();
  const detailDrawer = page.locator('.ant-drawer').filter({ hasText: '业务详情' });
  await expect(detailDrawer.getByText('客户拜访')).toBeVisible();
  await expect(detailDrawer.getByText('PROC-RUNTIME-001')).toBeVisible();
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
    actionBox.x + actionBox.width / 2 + captchaBox.width + actionBox.width,
    actionBox.y + actionBox.height / 2,
    { steps: 20 },
  );
  await page.mouse.up();
  await page.waitForTimeout(300);
  await page.getByRole('button', { name: 'login', exact: true }).click();
}

async function installApiFixtures(
  page: Page,
  state: {
    approved: boolean;
    retried: boolean;
    submitted: boolean;
    submittedData: Record<string, unknown>;
  },
) {
  await page.route('http://127.0.0.1:5556/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace(/^\/api/, '');
    const method = request.method();

    if (path === '/auth/login' && method === 'POST') {
      return ok(route, {
        accessToken: 'generic-runtime-token',
        refreshToken: 'generic-runtime-refresh',
      });
    }
    if (path === '/auth/me' && method === 'GET') {
      return ok(route, {
        homePath: '/lowcode/expense',
        id: 'U001',
        realName: 'Admin',
        roles: ['ADMIN'],
        username: 'admin',
      });
    }
    if (path === '/auth/codes' && method === 'GET') {
      return ok(route, [
        '/api/v1/forms/expense/instances:GET',
        '/api/v1/forms/expense/submit:POST',
        '/api/v1/lowcode-runtime/apps:GET',
        '/api/v1/lowcode-runtime/apps/*:GET',
        '/api/v1/lowcode-runtime/apps/*/instances:GET',
        '/api/v1/lowcode-runtime/apps/*/instances/*:GET',
        '/api/v1/lowcode-runtime/apps/*/actions/*:POST',
        '/api/v1/lowcode-runtime/apps/*/instances/*/retry-workflow:POST',
        '/api/v1/tasks/my-pending:GET',
        '/api/v1/tasks/my-completed:GET',
        '/api/v1/tasks/*/approve:POST',
      ]);
    }
    if (path === '/menu/all' && method === 'GET') {
      return ok(route, menus());
    }
    if (path === '/lowcode-runtime/apps/expense_report' && method === 'GET') {
      return ok(route, DESCRIPTOR);
    }
    if (path === '/lowcode-runtime/apps/expense_report/instances' && method === 'GET') {
      const records = state.submitted ? [currentFormInstance(state)] : [];
      return ok(route, { records, total: records.length });
    }
    if (
      path === '/lowcode-runtime/apps/expense_report/actions/submitAndLaunch' &&
      method === 'POST'
    ) {
      state.submitted = true;
      state.submittedData = request.postDataJSON().data;
      return ok(route, {
        actionCode: 'submitAndLaunch',
        formInstance: currentFormInstance(state, 'PENDING_WORKFLOW'),
        retryable: true,
        status: 'WORKFLOW_PENDING',
      });
    }
    if (
      path === '/lowcode-runtime/apps/expense_report/instances/LCI-RUNTIME-001/retry-workflow' &&
      method === 'POST'
    ) {
      state.retried = true;
      return ok(route, {
        actionCode: 'submitAndLaunch',
        formInstance: currentFormInstance(state),
        status: 'WORKFLOW_STARTED',
        workflow: {
          processInstanceId: 'PROC-RUNTIME-001',
          processKey: 'expense_report',
          status: 'RUNNING',
          version: 1,
        },
      });
    }
    if (
      path === '/lowcode-runtime/apps/expense_report/instances/LCI-RUNTIME-001' &&
      method === 'GET'
    ) {
      return ok(route, currentFormInstance(state));
    }
    if (path === '/tasks/my-pending' && method === 'GET') {
      return ok(route, {
        records: state.retried && !state.approved ? [TASK] : [],
        total: state.retried && !state.approved ? 1 : 0,
      });
    }
    if (path === '/tasks/my-completed' && method === 'GET') {
      return ok(route, {
        records: state.approved ? [{ ...TASK, status: 'APPROVED' }] : [],
        total: state.approved ? 1 : 0,
      });
    }
    if (path === '/tasks/task-expense-runtime/approve' && method === 'POST') {
      state.approved = true;
      return ok(route, {
        ...TASK,
        completedAt: '2026-07-18T20:03:00',
        status: 'APPROVED',
      });
    }

    return route.fulfill({
      body: JSON.stringify({ code: 40400, message: `UNMOCKED_API:${method}:${path}` }),
      contentType: 'application/json',
      status: 404,
    });
  });
}

function currentFormInstance(
  state: {
    approved: boolean;
    retried: boolean;
    submittedData: Record<string, unknown>;
  },
  overrideStatus?: string,
) {
  const workflowStatus =
    overrideStatus || (state.approved ? 'COMPLETED' : state.retried ? 'RUNNING' : 'PENDING_WORKFLOW');
  return {
    dataJson: JSON.stringify(state.submittedData),
    formDefinitionId: 'LC_FORM_EXPENSE_001',
    formDefinitionVersion: 1,
    formKey: 'expense',
    id: 'LCI-RUNTIME-001',
    processInstanceId: state.retried ? 'PROC-RUNTIME-001' : undefined,
    processKey: state.retried ? 'expense_report' : undefined,
    schemaHash: 'hash',
    status: 'SUBMITTED',
    submittedAt: '2026-07-18T20:02:00',
    submittedBy: 'admin',
    tenantId: 'GLOBAL',
    workflowStatus,
  };
}

function menus() {
  return [
    {
      children: [
        {
          authCode: '/api/v1/lowcode-runtime/apps:GET',
          component: '/lowcode/runtime/center',
          meta: { icon: 'mdi:apps', order: 10, title: '应用中心' },
          name: 'LowcodeAppCenter',
          path: '/lowcode/apps',
          type: 'menu',
        },
        {
          authCode: '/api/v1/lowcode-runtime/apps/*:GET',
          component: '/lowcode/runtime/app',
          meta: { hideInMenu: true, title: '快速应用' },
          name: 'LowcodeRuntimeApp',
          path: '/lowcode/apps/:appKey',
          type: 'menu',
        },
        {
          authCode: '/api/v1/forms/expense/instances:GET',
          component: '/lowcode/runtime/expense-compat',
          meta: { icon: 'mdi:receipt-text-edit-outline', order: 20, title: '费用报销' },
          name: 'LowcodeExpense',
          path: '/lowcode/expense',
          type: 'menu',
        },
        {
          authCode: '/api/v1/forms/expense/instances:GET',
          component: '/lowcode/expense/list',
          meta: { hideInMenu: true, title: '费用报销' },
          name: 'LowcodeExpenseLegacy',
          path: '/lowcode/expense-legacy',
          type: 'menu',
        },
      ],
      meta: { icon: 'mdi:application-braces-outline', order: 70, title: '快速开发' },
      name: 'LowcodeCenter',
      path: '/lowcode',
      type: 'catalog',
    },
    {
      children: [
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
