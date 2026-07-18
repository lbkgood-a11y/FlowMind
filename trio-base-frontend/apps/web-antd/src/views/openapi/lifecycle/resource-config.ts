import type {
  PayloadFormDefinition,
  PayloadFormField,
  PayloadFormReference,
} from './payload-form';

export interface LifecycleActionConfig {
  bodyless?: boolean;
  form?: PayloadFormDefinition;
  label: string;
  method?: 'POST' | 'PUT';
  path: string;
  targetLabel?: string;
  targetPlaceholder?: string;
  targetReference?: PayloadFormReference;
}

export interface LifecycleEditConfig {
  form: PayloadFormDefinition;
  loadPath?: string;
  method?: 'PUT';
  path: string;
  states?: string[];
}

export interface LifecycleResourceConfig {
  actions: LifecycleActionConfig[];
  assetType: string;
  createEndpoint: string;
  createForm: PayloadFormDefinition;
  description: string;
  edit?: LifecycleEditConfig;
  title: string;
  writePermission: string;
}

const authenticationOptions = enumOptions([
  'NONE',
  'API_KEY',
  'BASIC',
  'OAUTH2_CLIENT',
  'HMAC',
  'RSA',
  'MTLS',
]);
const environmentOptions = enumOptions(['DEV', 'TEST', 'PROD']);
const httpMethodOptions = enumOptions(['GET', 'POST', 'PUT', 'PATCH', 'DELETE']);
const mappingOperationOptions = enumOptions([
  'COPY',
  'MOVE',
  'CONSTANT',
  'DEFAULT',
  'TYPE_CONVERT',
  'CONCATENATE',
  'DATE_FORMAT',
  'COLLECTION_PROJECT',
  'VALUE_MAP',
]);
const riskOptions = enumOptions(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);

const applicationClientRef = refConfig('application-clients', {
  codePath: 'assetKey',
  labelPath: 'displayName',
});
const applicationRef = refConfig('applications');
const callbackVersionRef = refConfig('callback-versions');
const connectorRef = refConfig('connectors');
const connectorVersionRef = refConfig('connector-versions');
const mappingVersionRef = refConfig('mapping-versions');
const orchestrationRef = refConfig('orchestrations');
const orchestrationVersionRef = refConfig('orchestration-versions');
const policyRef = refConfig('policies');
const productRef = refConfig('products');
const productVersionRef = refConfig('product-versions', {
  codePath: 'detail.semanticVersion',
});
const releaseRef = refConfig('releases');
const routeRef = refConfig('routes');
const routeKeyRef = refConfig('routes', {
  valuePath: 'assetKey',
});
const routeVersionRef = refConfig('route-versions');
const structureRef = refConfig('structures');
const structureVersionRef = refConfig('structure-versions');
const subscriptionRef = refConfig('subscriptions');
const valueMapVersionRef = refConfig('value-map-versions');

const tenantField = text('tenantId', '租户 ID', '留空使用当前登录租户');
const ownerField = text('ownerId', '负责人 ID', '实施或资产负责人用户 ID', true);

const mappingRulesField: PayloadFormField = {
  addLabel: '添加映射规则',
  defaultValue: [
    {
      config: {},
      operation: 'COPY',
      order: 1,
      required: true,
      sourcePointer: '/id',
      targetPointer: '/id',
    },
  ],
  itemFields: [
    number('order', '顺序', 1, true),
    select('operation', '转换动作', mappingOperationOptions, 'COPY', true),
    text('sourcePointer', '源字段指针', '例如 /customer/name'),
    text('targetPointer', '目标字段指针', '例如 /buyer/name', true),
    {
      defaultValue: {},
      key: 'config',
      kind: 'json',
      label: '规则配置',
      rows: 5,
      wide: true,
    },
    boolean('required', '必填', true),
  ],
  key: 'rules',
  kind: 'array',
  label: '映射规则',
  wide: true,
};

const routePredicateField: PayloadFormField = {
  fields: [
    {
      addLabel: '添加匹配条件',
      defaultValue: [
        {
          name: 'X-OpenApi-Operation',
          operator: 'EQUALS',
          source: 'HEADER',
          value: 'createOrder',
        },
      ],
      itemFields: [
        select('source', '来源', enumOptions(['HEADER', 'QUERY', 'CLAIM']), 'HEADER', true),
        text('name', '字段名', '例如 X-OpenApi-Operation', true),
        select('operator', '匹配方式', enumOptions(['EQUALS', 'IN', 'PRESENT']), 'EQUALS', true),
        text('value', '匹配值', 'EQUALS 时填写'),
        tags('values', '可选值', 'IN 时填写多个值'),
      ],
      key: 'all',
      kind: 'array',
      label: '全部匹配条件',
      wide: true,
    },
  ],
  key: 'routePredicate',
  kind: 'object',
  label: '路由条件',
  wide: true,
};

const releaseNotesForm = form([
  group('发布说明', [
    textarea('releaseNotes', '发布说明', '说明本次 Release 变更、影响范围和回滚依据'),
  ]),
]);

const publishApprovalForm = form([
  group('发布确认', [
    boolean('assetOwnerApproved', '资产负责人已确认', false),
    boolean('platformAdministratorApproved', '平台管理员已确认', false),
    boolean('startNewCompatibilityLine', '开启新兼容线', false),
  ]),
]);

const rollbackForm = form([
  group('回滚目标', [
    select('environment', '环境', environmentOptions, 'TEST', true),
    reference('targetReleaseId', '目标 Release', releaseRef, '搜索 Release 快照', true),
  ]),
]);

const mappingPreviewForm = form([
  group('预览数据', [
    {
      defaultValue: { id: 'demo-id' },
      key: 'payload',
      kind: 'json',
      label: '待转换 payload',
      required: true,
      rows: 10,
      wide: true,
    },
  ]),
]);

const valueLookupForm = form([
  group('查找条件', [
    text('value', '输入值', '需要查找的枚举值', true),
    boolean('canonicalToExternal', '标准值转外部值', true),
  ]),
]);

const reasonForm = form([
  group('操作原因', [textarea('reason', '原因', '记录暂停或变更原因')]),
]);

const applicationClientCreateForm = form([
  group('环境客户端', [
    select('environment', '环境', environmentOptions, 'TEST', true),
    text('clientKey', '客户端 Key', '例如 order-demo-client', true),
    text('expiresAt', '过期时间', '例如 2026-12-31T23:59:59'),
  ]),
  group('访问约束', [
    {
      fields: [
        tags('allowedNetworks', '允许来源网络', '例如 10.0.0.0/24'),
      ],
      key: 'networkPolicy',
      kind: 'object',
      label: '网络策略',
      wide: true,
    },
    {
      fields: [
        boolean('requireTls', '要求 TLS', false),
      ],
      key: 'securityPolicy',
      kind: 'object',
      label: '安全策略',
      wide: true,
    },
  ]),
]);

const credentialRotateForm = form([
  group('凭证轮换', [
    select('authenticationType', '认证方式', authenticationOptions, 'API_KEY', true),
    text('newSecretReference', '新密钥引用', '例如 demo/order-client/api-key', true),
    number('overlapSeconds', '新旧凭证重叠秒', 0, true, 0),
    text('expiresAt', '过期时间', '例如 2026-12-31T23:59:59'),
  ]),
]);

const subscriptionUpgradeForm = form([
  group('目标版本', [
    reference('apiProductVersionId', '新产品版本', productVersionRef, '搜索 API 产品版本', true),
  ]),
]);

const structureCreateForm = form([
  group('基础信息', [
    tenantField,
    text('namespace', '命名空间', '例如 crm.order', true),
    text('structureKey', '结构标识', '例如 canonical-order-request', true),
    text('displayName', '显示名称', '例如 标准订单请求', true),
    textarea('description', '说明', '结构用途、边界和来源'),
    select(
      'structureKind',
      '结构类型',
      enumOptions(['CANONICAL', 'EXTERNAL', 'TENANT_EXTENSION']),
      'CANONICAL',
      true,
    ),
    select(
      'direction',
      '方向',
      enumOptions(['REQUEST', 'RESPONSE', 'BIDIRECTIONAL']),
      'REQUEST',
      true,
    ),
    text('ownerType', '负责人类型', '例如 TEAM 或 USER', true, 'TEAM'),
    ownerField,
    reference('parentStructureVersionId', '父版本', structureVersionRef, '兼容演进时搜索父版本'),
  ]),
  group('Schema', [
    {
      defaultValue: {
        $schema: 'https://json-schema.org/draft/2020-12/schema',
        properties: {
          id: { type: 'string' },
        },
        required: ['id'],
        type: 'object',
      },
      key: 'schemaContent',
      kind: 'json',
      label: 'JSON Schema',
      required: true,
      rows: 12,
      wide: true,
    },
    textarea('changeSummary', '变更说明', '首版创建或本次变更摘要'),
  ]),
]);

const structureVersionEditForm = form([
  group('Schema', [
    {
      defaultValue: {
        $schema: 'https://json-schema.org/draft/2020-12/schema',
        properties: {
          id: { type: 'string' },
        },
        required: ['id'],
        type: 'object',
      },
      key: 'schemaContent',
      kind: 'json',
      label: 'JSON Schema',
      required: true,
      rows: 12,
      wide: true,
    },
    {
      defaultValue: {},
      key: 'semanticChange',
      kind: 'json',
      label: '语义变更',
      rows: 5,
      wide: true,
    },
    textarea('changeSummary', '变更说明', '本次草稿变更摘要'),
  ]),
]);

const valueMapCreateForm = form([
  group('基础信息', [
    tenantField,
    text('valueMapKey', '值映射标识', '例如 order-status-map', true),
    text('displayName', '显示名称', '例如 订单状态映射', true),
    textarea('description', '说明', '枚举含义和适用系统'),
    ownerField,
    boolean('caseSensitive', '大小写敏感', false),
    select(
      'unmappedPolicy',
      '未命中策略',
      enumOptions(['FAIL', 'PASS_THROUGH', 'USE_DEFAULT']),
      'FAIL',
      true,
    ),
    text('defaultCanonicalValue', '默认标准值', 'USE_DEFAULT 时填写'),
    text('defaultExternalValue', '默认外部值', 'USE_DEFAULT 时填写'),
  ]),
  group('映射项', [
    {
      addLabel: '添加映射项',
      defaultValue: [{ canonicalValue: 'CREATED', externalValue: '10', order: 1 }],
      itemFields: [
        text('canonicalValue', '标准值', '例如 CREATED', true),
        text('externalValue', '外部值', '例如 10', true),
        number('order', '顺序', 1, true),
      ],
      key: 'entries',
      kind: 'array',
      label: '映射项',
      wide: true,
    },
  ]),
]);

const valueMapVersionEditForm = form([
  group('版本策略', [
    boolean('caseSensitive', '大小写敏感', false),
    select(
      'unmappedPolicy',
      '未命中策略',
      enumOptions(['FAIL', 'PASS_THROUGH', 'USE_DEFAULT']),
      'FAIL',
      true,
    ),
    text('defaultCanonicalValue', '默认标准值', 'USE_DEFAULT 时填写'),
    text('defaultExternalValue', '默认外部值', 'USE_DEFAULT 时填写'),
  ]),
  group('映射项', [
    {
      addLabel: '添加映射项',
      defaultValue: [{ canonicalValue: 'CREATED', externalValue: '10', order: 1 }],
      itemFields: [
        text('canonicalValue', '标准值', '例如 CREATED', true),
        text('externalValue', '外部值', '例如 10', true),
        number('order', '顺序', 1, true),
      ],
      key: 'entries',
      kind: 'array',
      label: '映射项',
      wide: true,
    },
  ]),
]);

const mappingCreateForm = form([
  group('基础信息', [
    tenantField,
    text('mappingKey', '映射标识', '例如 order-canonical-to-partner', true),
    text('displayName', '显示名称', '例如 标准订单到伙伴订单', true),
    textarea('description', '说明', '映射用途和适配系统'),
    select(
      'direction',
      '映射方向',
      enumOptions(['CANONICAL_TO_EXTERNAL', 'EXTERNAL_TO_CANONICAL']),
      'CANONICAL_TO_EXTERNAL',
      true,
    ),
    reference('canonicalStructureId', '标准结构', structureRef, '搜索标准结构', true),
    reference('externalStructureId', '外部结构', structureRef, '搜索外部结构', true),
    reference('sourceStructureVersionId', '源结构版本', structureVersionRef, '搜索源结构版本', true),
    reference('targetStructureVersionId', '目标结构版本', structureVersionRef, '搜索目标结构版本', true),
    ownerField,
  ]),
  group('规则', [mappingRulesField]),
]);

const mappingRulesEditForm = form([
  group('规则', [mappingRulesField]),
]);

const connectorCreateForm = form([
  group('基础信息', [
    tenantField,
    text('connectorKey', '连接器标识', '例如 partner-order-create', true),
    text('displayName', '显示名称', '例如 伙伴订单创建接口', true),
    ownerField,
  ]),
  group('请求配置', [
    text('baseUrl', 'Base URL', '例如 https://api.partner.example', true),
    text('operationPath', '接口路径', '必须以 / 开头，例如 /v1/orders', true),
    select('httpMethod', 'HTTP 方法', httpMethodOptions, 'POST', true),
    number('timeoutMillis', '超时毫秒', 3000, true, 1, 120_000),
    select(
      'operationClass',
      '操作类型',
      enumOptions(['READ_ONLY', 'STATE_CHANGING']),
      'STATE_CHANGING',
      true,
    ),
    select('authenticationType', '认证方式', authenticationOptions, 'NONE', true),
    text('secretReference', '密钥引用', '认证方式非 NONE 时填写'),
    number('responseSizeLimit', '响应大小上限', 1_048_576, true, 1, 52_428_800),
  ]),
  group('网络策略', [
    {
      fields: [
        tags('allowedHosts', '允许域名', '例如 api.partner.example'),
        boolean('allowPrivateNetwork', '允许私网地址', false),
      ],
      key: 'networkPolicy',
      kind: 'object',
      label: '网络策略',
      wide: true,
    },
  ]),
]);

const connectorVersionEditForm = form([
  group('请求配置', [
    text('baseUrl', 'Base URL', '例如 https://api.partner.example', true),
    text('operationPath', '接口路径', '必须以 / 开头，例如 /v1/orders', true),
    select('httpMethod', 'HTTP 方法', httpMethodOptions, 'POST', true),
    number('timeoutMillis', '超时毫秒', 3000, true, 1, 120_000),
    select(
      'operationClass',
      '操作类型',
      enumOptions(['READ_ONLY', 'STATE_CHANGING']),
      'STATE_CHANGING',
      true,
    ),
    select('authenticationType', '认证方式', authenticationOptions, 'NONE', true),
    text('secretReference', '密钥引用', '认证方式非 NONE 时填写'),
    number('responseSizeLimit', '响应大小上限', 1_048_576, true, 1, 52_428_800),
  ]),
  group('网络策略', [
    {
      fields: [
        tags('allowedHosts', '允许域名', '例如 api.partner.example'),
        boolean('allowPrivateNetwork', '允许私网地址', false),
      ],
      key: 'networkPolicy',
      kind: 'object',
      label: '网络策略',
      wide: true,
    },
  ]),
]);

const routeCreateForm = form([
  group('基础信息', [
    tenantField,
    text('routeKey', '路由标识', '例如 partner-order-create', true),
    text('displayName', '显示名称', '例如 伙伴订单创建路由', true),
    ownerField,
    select('environment', '环境', environmentOptions, 'TEST', true),
    number('priority', '优先级', 100, true),
    text('effectiveFrom', '生效开始时间', '例如 2026-07-18T09:00:00'),
    text('effectiveUntil', '生效结束时间', '例如 2026-12-31T23:59:59'),
    boolean('enabled', '启用', true),
    select(
      'executionMode',
      '执行模式',
      enumOptions(['SYNCHRONOUS', 'ORCHESTRATED']),
      'SYNCHRONOUS',
      true,
    ),
  ]),
  group('依赖版本', [
    reference('connectorVersionId', '连接器版本', connectorVersionRef, '同步路由选择连接器版本'),
    reference('requestMappingVersionId', '请求映射版本', mappingVersionRef, '入参转换映射版本'),
    reference('responseMappingVersionId', '响应映射版本', mappingVersionRef, '出参转换映射版本'),
    reference('orchestrationVersionId', '编排版本', orchestrationVersionRef, '编排路由选择编排版本'),
  ]),
  group('匹配条件', [routePredicateField]),
]);

const routeVersionEditForm = form([
  group('路由版本', [
    number('priority', '优先级', 100, true),
    text('effectiveFrom', '生效开始时间', '例如 2026-07-18T09:00:00'),
    text('effectiveUntil', '生效结束时间', '例如 2026-12-31T23:59:59'),
    boolean('enabled', '启用', true),
    select(
      'executionMode',
      '执行模式',
      enumOptions(['SYNCHRONOUS', 'ORCHESTRATED']),
      'SYNCHRONOUS',
      true,
    ),
  ]),
  group('依赖版本', [
    reference('connectorVersionId', '连接器版本', connectorVersionRef, '同步路由选择连接器版本'),
    reference('requestMappingVersionId', '请求映射版本', mappingVersionRef, '入参转换映射版本'),
    reference('responseMappingVersionId', '响应映射版本', mappingVersionRef, '出参转换映射版本'),
    reference('orchestrationVersionId', '编排版本', orchestrationVersionRef, '编排路由选择编排版本'),
  ]),
  group('匹配条件', [routePredicateField]),
]);

const productCreateForm = form([
  group('基础信息', [
    tenantField,
    text('productKey', '产品标识', '例如 order-openapi', true),
    text('displayName', '显示名称', '例如 订单开放 API', true),
    ownerField,
    textarea('audience', '受众', '适用业务方或伙伴范围'),
    select('riskLevel', '风险等级', riskOptions, 'MEDIUM', true),
    select(
      'visibility',
      '可见性',
      enumOptions(['PRIVATE', 'TENANT', 'PLATFORM_PUBLIC']),
      'TENANT',
    ),
  ]),
  group('版本与文档', [
    text('semanticVersion', '语义版本', '例如 1.0.0', true, '1.0.0'),
    select(
      'changeClassification',
      '变更级别',
      enumOptions(['PATCH', 'MINOR', 'MAJOR']),
      'MINOR',
      true,
    ),
    textarea('documentation', '文档', '产品说明、调用约束和示例'),
    textarea('terms', '条款', '接入条款或 SLA'),
    textarea('migrationNotice', '迁移说明', '破坏性变更或升级提示'),
    tags('defaultScopes', '默认 Scope', '例如 orders:read', ['orders:read']),
  ]),
  group('默认策略', [
    {
      fields: [
        number('ratePerSecond', '每秒请求数', 20, true, 1),
        number('burst', '突发容量', 40, true, 1),
      ],
      key: 'defaultTrafficPolicy',
      kind: 'object',
      label: '默认流控策略',
      wide: true,
    },
    {
      fields: [
        boolean('requireTls', '要求 TLS', true),
        tags('authenticationTypes', '允许认证方式', '例如 API_KEY、HMAC', ['API_KEY']),
      ],
      key: 'defaultSecurityPolicy',
      kind: 'object',
      label: '默认安全策略',
      wide: true,
    },
  ]),
  group('包含路由', [
    {
      addLabel: '添加产品路由',
      itemFields: [
        reference('routeKey', '路由标识', routeKeyRef, '搜索路由 key', true),
        reference('releaseSnapshotId', 'Release 快照', releaseRef, '搜索不可变 Release', true),
        tags('operations', '开放操作', '例如 createOrder'),
        tags('scopes', '路由 Scope', '例如 orders:write'),
        referenceMany(
          'canonicalStructureVersionIds',
          '标准结构版本',
          structureVersionRef,
          '搜索并选择多个结构版本',
        ),
      ],
      key: 'routes',
      kind: 'array',
      label: '产品路由',
      wide: true,
    },
  ]),
]);

const productVersionEditForm = form([
  group('版本与文档', [
    text('semanticVersion', '语义版本', '例如 1.0.1', true, '1.0.0'),
    select(
      'changeClassification',
      '变更级别',
      enumOptions(['PATCH', 'MINOR', 'MAJOR']),
      'MINOR',
      true,
    ),
    textarea('documentation', '文档', '产品说明、调用约束和示例'),
    textarea('terms', '条款', '接入条款或 SLA'),
    textarea('migrationNotice', '迁移说明', '破坏性变更或升级提示'),
    tags('scopes', '开放 Scope', '例如 orders:read', ['orders:read']),
  ]),
  group('版本策略', [
    {
      fields: [
        number('ratePerSecond', '每秒请求数', 20, true, 1),
        number('burst', '突发容量', 40, true, 1),
      ],
      key: 'trafficPolicy',
      kind: 'object',
      label: '流控策略',
      wide: true,
    },
    {
      fields: [
        boolean('requireTls', '要求 TLS', true),
        tags('authenticationTypes', '允许认证方式', '例如 API_KEY、HMAC', ['API_KEY']),
      ],
      key: 'securityPolicy',
      kind: 'object',
      label: '安全策略',
      wide: true,
    },
  ]),
  group('包含路由', [
    {
      addLabel: '添加产品路由',
      itemFields: [
        reference('routeKey', '路由标识', routeKeyRef, '搜索路由 key', true),
        reference('releaseSnapshotId', 'Release 快照', releaseRef, '搜索不可变 Release', true),
        tags('operations', '开放操作', '例如 createOrder'),
        tags('scopes', '路由 Scope', '例如 orders:write'),
        referenceMany(
          'canonicalStructureVersionIds',
          '标准结构版本',
          structureVersionRef,
          '搜索并选择多个结构版本',
        ),
      ],
      key: 'routes',
      kind: 'array',
      label: '产品路由',
      wide: true,
    },
  ]),
]);

const applicationCreateForm = form([
  group('基础信息', [
    tenantField,
    text('applicationKey', '应用标识', '例如 partner-a-order-app', true),
    text('displayName', '显示名称', '例如 伙伴 A 订单应用', true),
    ownerField,
    textarea('purpose', '接入目的', '说明调用场景和业务用途', true),
    select('riskLevel', '风险等级', riskOptions, 'MEDIUM', true),
  ]),
  group('联系人', [
    {
      addLabel: '添加联系人',
      defaultValue: [{ name: '实施负责人', role: 'PRIMARY' }],
      itemFields: [
        text('role', '角色', '例如 PRIMARY、TECH、BUSINESS', true),
        text('name', '姓名', '联系人姓名', true),
        text('email', '邮箱', '联系人邮箱'),
        text('phone', '电话', '联系人电话'),
      ],
      key: 'contacts',
      kind: 'array',
      label: '联系人',
      wide: true,
    },
  ]),
]);

const applicationEditForm = form([
  group('基础信息', [
    text('displayName', '显示名称', '例如 伙伴 A 订单应用', true),
    ownerField,
    textarea('purpose', '接入目的', '说明调用场景和业务用途', true),
    select('riskLevel', '风险等级', riskOptions, 'MEDIUM', true),
  ]),
]);

const subscriptionCreateForm = form([
  group('订阅对象', [
    reference('applicationClientId', '应用客户端', applicationClientRef, '搜索应用环境客户端', true),
    reference('apiProductVersionId', 'API 产品版本', productVersionRef, '搜索要订阅的产品版本', true),
    tags('requestedScopes', '申请 Scope', '例如 orders:read', ['orders:read']),
    text('effectiveFrom', '生效开始时间', '例如 2026-07-18T09:00:00'),
    text('effectiveUntil', '生效结束时间', '例如 2026-12-31T23:59:59'),
  ]),
  group('路由覆盖', [
    {
      addLabel: '添加路由覆盖',
      itemFields: [
        reference('routeKey', '路由标识', routeKeyRef, '搜索产品内路由 key', true),
        boolean('excluded', '排除该路由', false),
        tags('allowedOperations', '允许操作', '例如 createOrder'),
        tags('requiredScopes', '必需 Scope', '例如 orders:write'),
        {
          key: 'quotaOverride',
          kind: 'json',
          label: '配额覆盖',
          placeholder: '{"dailyQuota":1000}',
          rows: 4,
          wide: true,
        },
        tags('sourceNetworks', '来源网络', '例如 10.0.0.0/24'),
        referenceMany(
          'structureVersionIds',
          '结构版本限制',
          structureVersionRef,
          '搜索并选择多个结构版本',
        ),
        tags('fieldRestrictions', '字段限制', '例如 /amount'),
      ],
      key: 'routeOverrides',
      kind: 'array',
      label: '路由覆盖',
      wide: true,
    },
  ]),
]);

const callbackCreateForm = form([
  group('基础信息', [
    tenantField,
    text('displayName', '显示名称', '例如 伙伴订单回调', true),
    ownerField,
  ]),
  group('回调版本', [
    {
      fields: [
        select('environment', '环境', environmentOptions, 'TEST', true),
        reference('applicationClientId', '应用客户端', applicationClientRef, '搜索接收回调的应用客户端', true),
        select('authenticationType', '认证方式', authenticationOptions, 'HMAC', true),
        text('secretReference', '密钥引用', '验签密钥引用', true),
        reference('requestStructureVersionId', '请求结构版本', structureVersionRef, '搜索回调入参结构版本', true),
        reference('inboundMappingVersionId', '入站映射版本', mappingVersionRef, '搜索入站映射版本'),
        text('partnerEventIdPointer', '事件 ID 指针', '例如 /eventId', true, '/eventId'),
        text('correlationPointer', '关联值指针', '例如 /executionId', true, '/executionId'),
        select(
          'correlationType',
          '关联类型',
          enumOptions(['EXECUTION_ID', 'WORKFLOW_ID', 'IDEMPOTENCY_KEY']),
          'EXECUTION_ID',
          true,
        ),
        text('signalName', 'Signal 名称', 'Temporal Signal 名称', true, 'PartnerCallbackReceived'),
        number('replayWindowSeconds', '重放窗口秒', 3600, true, 30, 86_400),
        number('maxBodyBytes', '最大报文大小', 1_048_576, true, 1, 10_485_760),
        number('callbackPerMinute', '每分钟回调数', 120, true, 1),
        number('acknowledgementStatus', '应答状态码', 202, true, 200, 299),
        text(
          'acknowledgementContentType',
          '应答 Content-Type',
          '例如 application/json',
          true,
          'application/json',
        ),
        textarea('acknowledgementBody', '应答内容', '例如 {"accepted":true}', true, '{"accepted":true}'),
        {
          fields: [
            text('signatureHeader', '签名 Header', '例如 X-Signature', false, 'X-Signature'),
            text('timestampHeader', '时间戳 Header', '例如 X-Timestamp', false, 'X-Timestamp'),
            number('allowedClockSkewSeconds', '允许时钟偏差秒', 300, false, 1),
          ],
          key: 'securityPolicy',
          kind: 'object',
          label: '安全策略',
          wide: true,
        },
      ],
      key: 'version',
      kind: 'object',
      label: '版本配置',
      required: true,
      wide: true,
    },
  ]),
]);

const callbackVersionEditForm = form([
  group('回调版本', [
    select('environment', '环境', environmentOptions, 'TEST', true),
    reference('applicationClientId', '应用客户端', applicationClientRef, '搜索接收回调的应用客户端', true),
    select('authenticationType', '认证方式', authenticationOptions, 'HMAC', true),
    text('secretReference', '密钥引用', '验签密钥引用', true),
    reference('requestStructureVersionId', '请求结构版本', structureVersionRef, '搜索回调入参结构版本', true),
    reference('inboundMappingVersionId', '入站映射版本', mappingVersionRef, '搜索入站映射版本'),
    text('partnerEventIdPointer', '事件 ID 指针', '例如 /eventId', true, '/eventId'),
    text('correlationPointer', '关联值指针', '例如 /executionId', true, '/executionId'),
    select(
      'correlationType',
      '关联类型',
      enumOptions(['EXECUTION_ID', 'WORKFLOW_ID', 'IDEMPOTENCY_KEY']),
      'EXECUTION_ID',
      true,
    ),
    text('signalName', 'Signal 名称', 'Temporal Signal 名称', true, 'PartnerCallbackReceived'),
    number('replayWindowSeconds', '重放窗口秒', 3600, true, 30, 86_400),
    number('maxBodyBytes', '最大报文大小', 1_048_576, true, 1, 10_485_760),
    number('callbackPerMinute', '每分钟回调数', 120, true, 1),
    number('acknowledgementStatus', '应答状态码', 202, true, 200, 299),
    text(
      'acknowledgementContentType',
      '应答 Content-Type',
      '例如 application/json',
      true,
      'application/json',
    ),
    textarea('acknowledgementBody', '应答内容', '例如 {"accepted":true}', true, '{"accepted":true}'),
    {
      fields: [
        text('signatureHeader', '签名 Header', '例如 X-Signature', false, 'X-Signature'),
        text('timestampHeader', '时间戳 Header', '例如 X-Timestamp', false, 'X-Timestamp'),
        number('allowedClockSkewSeconds', '允许时钟偏差秒', 300, false, 1),
      ],
      key: 'securityPolicy',
      kind: 'object',
      label: '安全策略',
      wide: true,
    },
  ]),
]);

const orchestrationCreateForm = form([
  group('基础信息', [
    tenantField,
    text('orchestrationKey', '编排标识', '例如 partner-order-flow', true),
    text('displayName', '显示名称', '例如 伙伴订单编排', true),
    ownerField,
    text('schemaVersion', 'Schema 版本', '例如 1.0.0', true, '1.0.0'),
  ]),
  group('流程定义', [
    {
      fields: [
        {
          addLabel: '添加步骤',
          defaultValue: [
            {
              name: 'invokePartner',
              timeoutSeconds: 30,
              type: 'CONNECTOR_CALL',
            },
          ],
          itemFields: [
            text('name', '步骤名称', '例如 invokePartner', true),
            select(
              'type',
              '步骤类型',
              enumOptions(['CONNECTOR_CALL', 'WAIT_CALLBACK', 'TRANSFORM', 'SIGNAL', 'COMPENSATE']),
              'CONNECTOR_CALL',
              true,
            ),
            reference('connectorVersionId', '连接器版本', connectorVersionRef, '调用外部接口时选择'),
            reference('mappingVersionId', '映射版本', mappingVersionRef, '转换步骤选择映射版本'),
            reference('callbackProfileVersionId', '回调配置版本', callbackVersionRef, '等待回调时选择'),
            number('timeoutSeconds', '超时秒', 30, true, 1),
          ],
          key: 'steps',
          kind: 'array',
          label: '步骤',
          wide: true,
        },
        {
          fields: [
            number('maxAttempts', '最大重试次数', 3, true, 1),
            number('initialIntervalSeconds', '初始间隔秒', 2, true, 1),
          ],
          key: 'retryPolicy',
          kind: 'object',
          label: '重试策略',
          wide: true,
        },
      ],
      key: 'definitionContent',
      kind: 'object',
      label: '编排定义',
      required: true,
      wide: true,
    },
  ]),
]);

const orchestrationVersionEditForm = form([
  group('流程定义', [
    text('schemaVersion', 'Schema 版本', '例如 1.0.0', true, '1.0.0'),
    {
      fields: [
        {
          addLabel: '添加步骤',
          defaultValue: [
            {
              name: 'invokePartner',
              timeoutSeconds: 30,
              type: 'CONNECTOR_CALL',
            },
          ],
          itemFields: [
            text('name', '步骤名称', '例如 invokePartner', true),
            select(
              'type',
              '步骤类型',
              enumOptions(['CONNECTOR_CALL', 'WAIT_CALLBACK', 'TRANSFORM', 'SIGNAL', 'COMPENSATE']),
              'CONNECTOR_CALL',
              true,
            ),
            reference('connectorVersionId', '连接器版本', connectorVersionRef, '调用外部接口时选择'),
            reference('mappingVersionId', '映射版本', mappingVersionRef, '转换步骤选择映射版本'),
            reference('callbackProfileVersionId', '回调配置版本', callbackVersionRef, '等待回调时选择'),
            number('timeoutSeconds', '超时秒', 30, true, 1),
          ],
          key: 'steps',
          kind: 'array',
          label: '步骤',
          wide: true,
        },
        {
          fields: [
            number('maxAttempts', '最大重试次数', 3, true, 1),
            number('initialIntervalSeconds', '初始间隔秒', 2, true, 1),
          ],
          key: 'retryPolicy',
          kind: 'object',
          label: '重试策略',
          wide: true,
        },
      ],
      key: 'definitionContent',
      kind: 'object',
      label: '编排定义',
      required: true,
      wide: true,
    },
  ]),
]);

const policyCreateForm = form([
  group('策略范围', [
    tenantField,
    select('environment', '环境', environmentOptions, 'TEST', true),
    {
      ...select(
        'scopeType',
        '作用范围',
        enumOptions(['TENANT', 'CLIENT', 'PRODUCT', 'ROUTE', 'OPERATION', 'SUBSCRIPTION']),
        'ROUTE',
        true,
      ),
      clearKeysOnChange: ['scopeId'],
    },
    {
      key: 'scopeId',
      kind: 'reference',
      label: '范围对象',
      placeholder: '按作用范围搜索对象；租户或操作范围填写业务范围值',
      referenceDependsOn: 'scopeType',
      referenceMap: {
        CLIENT: applicationClientRef,
        PRODUCT: productRef,
        ROUTE: routeKeyRef,
        SUBSCRIPTION: subscriptionRef,
      },
      required: true,
    },
  ]),
  group('策略内容', [
    {
      fields: [
        number('ratePerSecond', '每秒请求数', 20, true, 1),
        number('burst', '突发容量', 40, true, 1),
        number('dailyQuota', '每日配额', 10_000, true, 1),
        number('maxBodyBytes', '最大报文大小', 1_048_576, true, 1),
        number('maxConcurrency', '最大并发', 20, true, 1),
        number('maxActiveWorkflows', '最大活动工作流', 100, true, 1),
        number('callbackPerMinute', '每分钟回调数', 120, true, 1),
        number('signatureWindowSeconds', '签名窗口秒', 300, true, 1),
        boolean('requireTls', '要求 TLS', true),
        boolean('queueOnConcurrency', '并发超限排队', false),
        tags('allowedNetworks', '允许来源网络', '例如 10.0.0.0/24'),
        tags('authenticationTypes', '允许认证方式', '例如 API_KEY、HMAC', ['API_KEY']),
        {
          defaultValue: { mask: true, reject: ['idCard', 'bankCard'] },
          key: 'sensitiveFieldPolicy',
          kind: 'json',
          label: '敏感字段策略',
          rows: 5,
          wide: true,
        },
        number('securityViolationThreshold', '安全违规阈值', 5, false, 1),
      ],
      key: 'policyContent',
      kind: 'object',
      label: '策略内容',
      required: true,
      wide: true,
    },
  ]),
]);

export const lifecycleResources: Record<string, LifecycleResourceConfig> = {
  applications: {
    actions: [
      action('创建客户端', '/openapi/management/applications/{id}/clients', applicationClientCreateForm, {
        targetLabel: '目标应用',
        targetReference: applicationRef,
      }),
      action('激活客户端', '/openapi/management/applications/clients/{id}/activate', undefined, {
        targetLabel: '目标客户端',
        targetReference: applicationClientRef,
      }),
      action('暂停客户端', '/openapi/management/applications/clients/{id}/suspend', reasonForm, {
        targetLabel: '目标客户端',
        targetReference: applicationClientRef,
      }),
      action('重新激活客户端', '/openapi/management/applications/clients/{id}/reactivate', undefined, {
        targetLabel: '目标客户端',
        targetReference: applicationClientRef,
      }),
      action('撤销客户端', '/openapi/management/applications/clients/{id}/revoke', undefined, {
        targetLabel: '目标客户端',
        targetReference: applicationClientRef,
      }),
      action('轮换客户端凭证', '/openapi/management/applications/clients/{id}/credentials/rotate', credentialRotateForm, {
        targetLabel: '目标客户端',
        targetReference: applicationClientRef,
      }),
      action('提交审批', '/openapi/management/applications/{id}/submit', undefined, {
        targetLabel: '目标应用',
        targetReference: applicationRef,
      }),
      action('激活', '/openapi/management/applications/{id}/activate', undefined, {
        targetLabel: '目标应用',
        targetReference: applicationRef,
      }),
      action('暂停', '/openapi/management/applications/{id}/suspend', reasonForm, {
        targetLabel: '目标应用',
        targetReference: applicationRef,
      }),
      action('重新激活', '/openapi/management/applications/{id}/reactivate', undefined, {
        targetLabel: '目标应用',
        targetReference: applicationRef,
      }),
      action('撤销', '/openapi/management/applications/{id}/revoke', undefined, {
        targetLabel: '目标应用',
        targetReference: applicationRef,
      }),
    ],
    assetType: 'applications',
    createEndpoint: '/openapi/management/applications',
    createForm: applicationCreateForm,
    description: '应用、环境客户端、凭证轮换与安全状态',
    edit: edit('/openapi/management/applications/{id}', applicationEditForm, {
      loadPath: '/openapi/management/applications/{id}',
      states: ['DRAFT', 'PENDING_APPROVAL'],
    }),
    title: '接入应用',
    writePermission: '/api/v1/openapi/management/applications:POST',
  },
  callbacks: {
    actions: [
      action('发布版本', '/openapi/management/callback-profiles/versions/{id}/publish', undefined, {
        targetLabel: '目标回调版本',
        targetReference: callbackVersionRef,
      }),
      action('弃用版本', '/openapi/management/callback-profiles/versions/{id}/deprecate', undefined, {
        targetLabel: '目标回调版本',
        targetReference: callbackVersionRef,
      }),
    ],
    assetType: 'callback-versions',
    createEndpoint: '/openapi/management/callback-profiles',
    createForm: callbackCreateForm,
    description: '验签、关联、去重、应答与工作流 Signal',
    edit: edit('/openapi/management/callback-profiles/versions/{id}', callbackVersionEditForm, {
      loadPath: '/openapi/management/callback-profiles/versions/{id}',
    }),
    title: '回调配置',
    writePermission: '/api/v1/openapi/management/callback-profiles:POST',
  },
  connectors: {
    actions: [
      action('发布版本', '/openapi/management/connectors/versions/{id}/publish', undefined, {
        targetLabel: '目标连接器版本',
        targetReference: connectorVersionRef,
      }),
      action('弃用版本', '/openapi/management/connectors/versions/{id}/deprecate', undefined, {
        targetLabel: '目标连接器版本',
        targetReference: connectorVersionRef,
      }),
      action('归档', '/openapi/management/connectors/{id}/archive', undefined, {
        targetLabel: '目标连接器',
        targetReference: connectorRef,
      }),
    ],
    assetType: 'connector-versions',
    createEndpoint: '/openapi/management/connectors',
    createForm: connectorCreateForm,
    description: '受控端点、网络策略与凭证引用',
    edit: edit('/openapi/management/connectors/versions/{id}', connectorVersionEditForm, {
      loadPath: '/openapi/management/connectors/versions/{id}',
    }),
    title: '连接器',
    writePermission: '/api/v1/openapi/management/connectors:POST',
  },
  mappings: {
    actions: [
      action('发布版本', '/openapi/management/mappings/versions/{id}/publish', undefined, {
        targetLabel: '目标映射版本',
        targetReference: mappingVersionRef,
      }),
      action('预览转换', '/openapi/management/mappings/versions/{id}/preview', mappingPreviewForm, {
        targetLabel: '目标映射版本',
        targetReference: mappingVersionRef,
      }),
    ],
    assetType: 'mapping-versions',
    createEndpoint: '/openapi/management/mappings',
    createForm: mappingCreateForm,
    description: '字段映射、转换预览与契约测试',
    edit: edit('/openapi/management/mappings/versions/{id}/rules', mappingRulesEditForm, {
      loadPath: '/openapi/management/mappings/versions/{id}',
    }),
    title: '字段映射',
    writePermission: '/api/v1/openapi/management/mappings:POST',
  },
  orchestrations: {
    actions: [
      action('发布版本', '/openapi/management/orchestrations/versions/{id}/publish', undefined, {
        targetLabel: '目标编排版本',
        targetReference: orchestrationVersionRef,
      }),
      action('弃用版本', '/openapi/management/orchestrations/versions/{id}/deprecate', undefined, {
        targetLabel: '目标编排版本',
        targetReference: orchestrationVersionRef,
      }),
      action('归档', '/openapi/management/orchestrations/{id}/archive', undefined, {
        targetLabel: '目标编排',
        targetReference: orchestrationRef,
      }),
    ],
    assetType: 'orchestration-versions',
    createEndpoint: '/openapi/management/orchestrations',
    createForm: orchestrationCreateForm,
    description: 'Temporal 多步骤编排、等待与补偿',
    edit: edit('/openapi/management/orchestrations/versions/{id}', orchestrationVersionEditForm, {
      loadPath: '/openapi/management/orchestrations/versions/{id}',
    }),
    title: '流程编排',
    writePermission: '/api/v1/openapi/management/orchestrations:POST',
  },
  policies: {
    actions: [
      action('发布策略版本', '/openapi/management/policies/{id}/publish', undefined, {
        targetLabel: '目标策略',
        targetReference: policyRef,
      }),
    ],
    assetType: 'policies',
    createEndpoint: '/openapi/management/policies',
    createForm: policyCreateForm,
    description: '限流、配额、并发、安全策略与快照',
    edit: edit('/openapi/management/policies/{id}', policyCreateForm),
    title: '流控与安全策略',
    writePermission: '/api/v1/openapi/management/products:POST',
  },
  products: {
    actions: [
      action('发布产品版本', '/openapi/management/products/versions/{id}/publish', undefined, {
        targetLabel: '目标产品版本',
        targetReference: productVersionRef,
      }),
      action('弃用产品版本', '/openapi/management/products/versions/{id}/deprecate', undefined, {
        targetLabel: '目标产品版本',
        targetReference: productVersionRef,
      }),
      action('归档产品', '/openapi/management/products/{id}/archive', undefined, {
        targetLabel: '目标产品',
        targetReference: productRef,
      }),
    ],
    assetType: 'product-versions',
    createEndpoint: '/openapi/management/products',
    createForm: productCreateForm,
    description: '开放能力、版本、范围与默认策略',
    edit: edit('/openapi/management/products/versions/{id}', productVersionEditForm, {
      loadPath: '/openapi/management/products/versions/{id}',
    }),
    title: 'API 产品',
    writePermission: '/api/v1/openapi/management/products:POST',
  },
  routes: {
    actions: [
      action('发布路由版本', '/openapi/management/routes/versions/{id}/publish', undefined, {
        targetLabel: '目标路由版本',
        targetReference: routeVersionRef,
      }),
      action('创建 Release', '/openapi/management/routes/versions/{id}/releases', releaseNotesForm, {
        targetLabel: '目标路由版本',
        targetReference: routeVersionRef,
      }),
      action('激活 Release', '/openapi/management/releases/{id}/activate', undefined, {
        targetLabel: '目标 Release',
        targetReference: releaseRef,
      }),
      action('回滚路由', '/openapi/management/routes/{id}/rollback', rollbackForm, {
        targetLabel: '目标路由',
        targetReference: routeRef,
      }),
    ],
    assetType: 'route-versions',
    createEndpoint: '/openapi/management/routes',
    createForm: routeCreateForm,
    description: '路由条件、不可变 Release、激活与回滚',
    edit: edit('/openapi/management/routes/versions/{id}', routeVersionEditForm, {
      loadPath: '/openapi/management/routes/versions/{id}',
    }),
    title: '路由与发布',
    writePermission: '/api/v1/openapi/management/routes:POST',
  },
  structures: {
    actions: [
      action('发布版本', '/openapi/management/structures/versions/{id}/publish', publishApprovalForm, {
        targetLabel: '目标结构版本',
        targetReference: structureVersionRef,
      }),
      action('弃用版本', '/openapi/management/structures/versions/{id}/deprecate', undefined, {
        targetLabel: '目标结构版本',
        targetReference: structureVersionRef,
      }),
      action('归档版本', '/openapi/management/structures/versions/{id}/archive', undefined, {
        targetLabel: '目标结构版本',
        targetReference: structureVersionRef,
      }),
    ],
    assetType: 'structure-versions',
    createEndpoint: '/openapi/management/structures',
    createForm: structureCreateForm,
    description: '标准/外部结构、版本、兼容性与导入导出',
    edit: edit('/openapi/management/structures/versions/{id}', structureVersionEditForm, {
      loadPath: '/openapi/management/structures/versions/{id}',
    }),
    title: '标准与外部结构',
    writePermission: '/api/v1/openapi/management/structures:POST',
  },
  subscriptions: {
    actions: [
      action('激活订阅', '/openapi/management/subscriptions/{id}/activate', undefined, {
        targetLabel: '目标订阅',
        targetReference: subscriptionRef,
      }),
      action('升级订阅', '/openapi/management/subscriptions/{id}/upgrade', subscriptionUpgradeForm, {
        targetLabel: '目标订阅',
        targetReference: subscriptionRef,
      }),
      action('暂停订阅', '/openapi/management/subscriptions/{id}/suspend', reasonForm, {
        targetLabel: '目标订阅',
        targetReference: subscriptionRef,
      }),
      action('撤销订阅', '/openapi/management/subscriptions/{id}/revoke', undefined, {
        targetLabel: '目标订阅',
        targetReference: subscriptionRef,
      }),
    ],
    assetType: 'subscriptions',
    createEndpoint: '/openapi/management/subscriptions',
    createForm: subscriptionCreateForm,
    description: '产品订阅、版本升级、暂停撤销与双重审批',
    title: '订阅与审批',
    writePermission: '/api/v1/openapi/management/applications:POST',
  },
  'value-maps': {
    actions: [
      action('发布版本', '/openapi/management/value-maps/versions/{id}/publish', undefined, {
        targetLabel: '目标值映射版本',
        targetReference: valueMapVersionRef,
      }),
      action('值查找', '/openapi/management/value-maps/versions/{id}/lookup', valueLookupForm, {
        targetLabel: '目标值映射版本',
        targetReference: valueMapVersionRef,
      }),
    ],
    assetType: 'value-map-versions',
    createEndpoint: '/openapi/management/value-maps',
    createForm: valueMapCreateForm,
    description: '正反向枚举映射、默认值与未命中策略',
    edit: edit('/openapi/management/value-maps/versions/{id}', valueMapVersionEditForm, {
      loadPath: '/openapi/management/value-maps/versions/{id}',
    }),
    title: '值映射',
    writePermission: '/api/v1/openapi/management/mappings:POST',
  },
};

function action(
  label: string,
  path: string,
  formDefinition?: PayloadFormDefinition,
  overrides: Partial<LifecycleActionConfig> = {},
): LifecycleActionConfig {
  return {
    bodyless: !formDefinition,
    form: formDefinition,
    label,
    path,
    targetPlaceholder: overrides.targetPlaceholder ?? '搜索选择目标资产',
    ...overrides,
  };
}

function edit(
  path: string,
  formDefinition: PayloadFormDefinition,
  overrides: Partial<LifecycleEditConfig> = {},
): LifecycleEditConfig {
  return {
    form: formDefinition,
    method: 'PUT',
    path,
    states: ['DRAFT'],
    ...overrides,
  };
}

function boolean(key: string, label: string, defaultValue: boolean): PayloadFormField {
  return { defaultValue, key, kind: 'boolean', label };
}

function enumOptions(values: string[]) {
  return values.map((value) => ({ label: value, value }));
}

function form(groups: PayloadFormDefinition['groups']): PayloadFormDefinition {
  return { groups };
}

function group(title: string, fields: PayloadFormField[]): PayloadFormDefinition['groups'][number] {
  return { fields, title };
}

function number(
  key: string,
  label: string,
  defaultValue?: number,
  required = false,
  min?: number,
  max?: number,
): PayloadFormField {
  return { defaultValue, key, kind: 'number', label, max, min, required };
}

function refConfig(
  assetType: string,
  overrides: Partial<PayloadFormReference> = {},
): PayloadFormReference {
  return {
    assetType,
    codePath: 'assetKey',
    labelPath: 'displayName',
    valuePath: 'id',
    ...overrides,
  };
}

function reference(
  key: string,
  label: string,
  source: PayloadFormReference,
  placeholder: string,
  required = false,
  defaultValue?: string,
): PayloadFormField {
  return {
    defaultValue,
    key,
    kind: 'reference',
    label,
    placeholder,
    reference: source,
    required,
  };
}

function referenceMany(
  key: string,
  label: string,
  source: PayloadFormReference,
  placeholder: string,
  defaultValue: string[] = [],
): PayloadFormField {
  return {
    defaultValue,
    key,
    kind: 'reference',
    label,
    multiple: true,
    placeholder,
    reference: source,
    wide: true,
  };
}

function select(
  key: string,
  label: string,
  options: PayloadFormField['options'],
  defaultValue?: string,
  required = false,
): PayloadFormField {
  return { defaultValue, key, kind: 'select', label, options, required };
}

function tags(
  key: string,
  label: string,
  placeholder: string,
  defaultValue: string[] = [],
): PayloadFormField {
  return { defaultValue, key, kind: 'tags', label, placeholder, wide: true };
}

function text(
  key: string,
  label: string,
  placeholder: string,
  required = false,
  defaultValue?: string,
): PayloadFormField {
  return { defaultValue, key, kind: 'text', label, placeholder, required };
}

function textarea(
  key: string,
  label: string,
  placeholder: string,
  required = false,
  defaultValue?: string,
): PayloadFormField {
  return {
    defaultValue,
    key,
    kind: 'textarea',
    label,
    placeholder,
    required,
    rows: 4,
    wide: true,
  };
}
