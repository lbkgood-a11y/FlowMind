type OperationCategory =
  | 'business-action'
  | 'document-runtime'
  | 'management'
  | 'multi-table'
  | 'query';

type PageFamily =
  | 'lowcode'
  | 'openapi'
  | 'operations'
  | 'process'
  | 'system';

interface MigratedOperationPage {
  categories: OperationCategory[];
  family: PageFamily;
  file: string;
  notes?: string;
  pattern: 'document' | 'master-detail' | 'multi-table' | 'single-table';
  primitives: string[];
}

interface OperationPageException {
  file: string;
  reason: string;
}

const MIGRATED_OPERATION_PAGES: MigratedOperationPage[] = [
  {
    categories: ['management', 'query'],
    family: 'system',
    file: 'views/system/user/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'system',
    file: 'views/system/org/list.vue',
    pattern: 'master-detail',
    primitives: ['BusinessPageScaffold', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'system',
    file: 'views/system/dept/list.vue',
    notes: 'Department domain API is still pending; the page uses the standard shell and toolbar.',
    pattern: 'master-detail',
    primitives: ['BusinessPageScaffold', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'system',
    file: 'views/system/menu/list.vue',
    notes: 'Menu workbench keeps its domain-specific tree/edit orchestration.',
    pattern: 'master-detail',
    primitives: ['BusinessPageScaffold'],
  },
  {
    categories: ['management', 'query'],
    family: 'system',
    file: 'views/system/role/list.vue',
    pattern: 'master-detail',
    primitives: ['BusinessPageScaffold'],
  },
  {
    categories: ['management', 'query'],
    family: 'system',
    file: 'views/system/dictionary/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'system',
    file: 'views/system/config/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['query'],
    family: 'system',
    file: 'views/system/session/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['query'],
    family: 'system',
    file: 'views/system/audit-log/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'system',
    file: 'views/system/authz/index.vue',
    pattern: 'master-detail',
    primitives: ['BusinessPageScaffold', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['business-action', 'query'],
    family: 'process',
    file: 'views/process/task/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessActionButton', 'BusinessPageScaffold', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['business-action', 'query'],
    family: 'process',
    file: 'views/process/instance/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessActionButton', 'BusinessPageScaffold'],
  },
  {
    categories: ['management', 'query'],
    family: 'process',
    file: 'views/process/package/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management'],
    family: 'process',
    file: 'views/process/designer/index.vue',
    notes: 'Designer canvas internals remain owned by the process designer component.',
    pattern: 'document',
    primitives: ['BusinessPageScaffold', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'lowcode',
    file: 'views/lowcode/form/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['document-runtime', 'query'],
    family: 'lowcode',
    file: 'views/lowcode/runtime/center.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['business-action', 'document-runtime'],
    family: 'lowcode',
    file: 'views/lowcode/runtime/app.vue',
    pattern: 'document',
    primitives: [
      'BusinessActionButton',
      'CompactTableFrame',
      'DocumentActionBar',
      'DocumentHeader',
      'DocumentPage',
    ],
  },
  {
    categories: ['multi-table', 'query'],
    family: 'openapi',
    file: 'views/openapi/workbench/index.vue',
    pattern: 'multi-table',
    primitives: ['BusinessActionButton', 'BusinessPageScaffold', 'CompactTableFrame', 'CompactToolbar', 'MultiTableLayout'],
  },
  {
    categories: ['multi-table', 'query'],
    family: 'openapi',
    file: 'views/openapi/lifecycle/overview.vue',
    pattern: 'multi-table',
    primitives: ['BusinessPageScaffold', 'CompactToolbar', 'MultiTableLayout'],
  },
  {
    categories: ['document-runtime', 'management'],
    family: 'openapi',
    file: 'views/openapi/lifecycle/resource.vue',
    pattern: 'document',
    primitives: [
      'BusinessPageScaffold',
      'CompactQueryBar',
      'CompactTableFrame',
      'CompactToolbar',
      'SplitLayout',
    ],
  },
  {
    categories: ['query'],
    family: 'openapi',
    file: 'views/openapi/operations/executions.vue',
    notes: 'Delegates to the migrated OpenAPI workbench entry.',
    pattern: 'multi-table',
    primitives: [],
  },
  {
    categories: ['query'],
    family: 'openapi',
    file: 'views/openapi/operations/quarantine.vue',
    notes: 'Delegates to the migrated OpenAPI workbench entry.',
    pattern: 'multi-table',
    primitives: [],
  },
  {
    categories: ['management', 'query'],
    family: 'operations',
    file: 'views/operations/announcement/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'operations',
    file: 'views/operations/file/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'operations',
    file: 'views/operations/import-export/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'operations',
    file: 'views/operations/job/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
  {
    categories: ['management', 'query'],
    family: 'operations',
    file: 'views/operations/message/list.vue',
    pattern: 'single-table',
    primitives: ['BusinessPageScaffold', 'CompactQueryBar', 'CompactTableFrame', 'CompactToolbar'],
  },
];

const OPERATION_PAGE_EXCEPTIONS: OperationPageException[] = [
  {
    file: 'views/_core/**',
    reason: 'Vben shell-auth/profile/fallback pages are shell pages, not TrioBase business operation pages.',
  },
  {
    file: 'views/dashboard/**',
    reason: 'Dashboard pages are analytic surfaces and will be standardized separately.',
  },
  {
    file: 'views/demos/**',
    reason: 'Demo pages are not production business operation pages.',
  },
  {
    file: 'views/openapi/lifecycle/components/**',
    reason: 'Nested resource editor components inherit the page standard from lifecycle resource pages.',
  },
];

export type { MigratedOperationPage, OperationPageException };
export { MIGRATED_OPERATION_PAGES, OPERATION_PAGE_EXCEPTIONS };
