const BUSINESS_ERROR_MESSAGES: Record<string, string> = {
  APPLICATION_RUNTIME_ACTION_METADATA_INVALID: '应用动作配置无效，请联系管理员检查发布元数据',
  APPLICATION_RUNTIME_ACTION_NOT_FOUND: '当前应用未配置该操作或你没有操作权限',
  APPLICATION_RUNTIME_ACTION_REQUIRED: '请选择要执行的应用操作',
  APPLICATION_RUNTIME_ACTION_UNSUPPORTED: '当前应用操作暂不支持',
  APPLICATION_RUNTIME_DATA_INVALID: '业务数据格式无效，无法继续处理',
  APPLICATION_RUNTIME_INSTANCE_NOT_FOUND: '业务记录不存在或不属于当前应用',
  APPLICATION_RUNTIME_NOT_FOUND: '快速应用不存在、未发布或当前用户不可见',
  APPLICATION_RUNTIME_PAGE_NOT_FOUND: '当前应用缺少必要页面配置',
  APPLICATION_RUNTIME_PERMISSION_DENIED: '你没有访问该快速应用的权限',
  APPLICATION_RUNTIME_RETRY_ACTION_REQUIRED: '当前应用需要指定流程重试动作',
  APPLICATION_RUNTIME_WORKFLOW_NOT_PENDING: '该记录当前不处于待启动流程状态',
  APPLICATION_WORKFLOW_VERSION_CONFLICT: '流程版本已变化，请刷新应用后重新提交',
  DATE_FIELD_MUST_BE_STRING: '日期字段必须使用文本类型承载',
  DRAFT_CANNOT_CREATE_NEW_VERSION: '草稿不能再派生新版本，请先发布或下线当前版本',
  FORM_DEFINITION_MUTATION_DENIED: '当前租户无权修改该表单版本',
  FORM_DEFINITION_NOT_FOUND: '表单定义不存在或当前租户不可见',
  FORM_DEFINITION_NOT_PUBLISHED: '只能使用已发布的表单版本',
  FORM_DRAFT_ALREADY_EXISTS: '当前表单已有草稿版本，请先编辑或发布该草稿',
  FORM_FIELD_KEY_DUPLICATE: '存在重复字段标识',
  FORM_FIELD_KEY_REQUIRED: '字段标识不能为空',
  FORM_FIELD_NOT_IN_SCHEMA: '字段清单与表单 Schema 不一致',
  FORM_KEY_NOT_FOUND: '表单标识不存在或未发布',
  FORM_KEY_OR_NAME_REQUIRED: '表单标识和表单名称不能为空',
  FORM_REQUIRED_FIELD_NOT_FOUND: '必填字段必须先在字段列表中定义',
  FORM_REQUIRED_MUST_BE_ARRAY: '必填字段配置必须是数组',
  FORM_SCHEMA_PROPERTIES_MUST_BE_OBJECT: '表单字段定义必须是对象结构',
  FORM_SCHEMA_REQUIRED: '请先配置表单字段',
  FORM_SCHEMA_ROOT_MUST_BE_OBJECT: '表单 Schema 根节点必须是对象',
  INVALID_FORM_ENUM: '下拉字段必须配置至少一个有效选项',
  INVALID_FORM_FIELD_SCHEMA: '字段 Schema 配置不正确',
  INVALID_FORM_SCHEMA: '表单 Schema 不是合法 JSON Schema',
  INVALID_FORM_UI_SCHEMA: '表单 UI Schema 不是合法 JSON 对象',
  ONLY_DRAFT_CAN_BE_MODIFIED: '只有草稿版本可以编辑，已发布版本请先派生新版本',
  ONLY_DRAFT_CAN_BE_PUBLISHED: '只有草稿版本可以发布',
  ONLY_PUBLISHED_CAN_BE_OFFLINE: '只有已发布版本可以下线',
  UNREGISTERED_FORM_WIDGET: '存在未注册的表单控件，请选择受支持控件',
  UNSUPPORTED_FORM_FIELD_TYPE: '存在不支持的字段类型',
};

const FIELD_ERROR_MESSAGES: Record<string, string> = {
  INVALID_VALUE: '字段值不符合要求',
  OUT_OF_RANGE: '字段值超出允许范围',
  REQUIRED: '必填字段未填写',
  TYPE_MISMATCH: '字段类型不正确',
  UNKNOWN_FIELD: '提交了未定义字段',
};

export function formatApiErrorMessage(
  message: string,
  data?: unknown,
): string | undefined {
  const fieldErrorMessage = formatFieldErrors(data);
  if (fieldErrorMessage) {
    return fieldErrorMessage;
  }
  const normalized = message.trim();
  return BUSINESS_ERROR_MESSAGES[normalized];
}

function formatFieldErrors(data?: unknown) {
  const fieldErrors = (data as { fieldErrors?: unknown[] } | undefined)?.fieldErrors;
  if (!Array.isArray(fieldErrors) || fieldErrors.length === 0) {
    return undefined;
  }
  return fieldErrors
    .slice(0, 3)
    .map((item) => {
      const error = item as Record<string, unknown>;
      const field = String(error.field ?? '').trim();
      const code = String(error.code ?? '').trim();
      const fallback = String(error.message ?? '').trim();
      const label = field ? `${field}: ` : '';
      return `${label}${FIELD_ERROR_MESSAGES[code] || fallback || '字段值无效'}`;
    })
    .join('；');
}
