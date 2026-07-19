import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { expect, test } from '@playwright/test';

const srcRoot = fileURLToPath(new URL('../..', import.meta.url));

test.describe('global action migrated page coverage', () => {
  test('covers lowcode submit and workflow retry through dispatchAction', () => {
    const source = read('views/lowcode/runtime/app.vue');

    expect(source).toContain('useActionDispatch');
    expect(source).toContain('dispatchAction(');
    expect(source).toContain('lowcodeGlobalActionType(action)');
    expect(source).toContain('ACTION_TYPES.lowcodeWorkflowRetry');
    expect(source).toContain('createRuntimeDraftKey');
    expect(source).toContain('createRuntimeRetryKey');
    expect(source).not.toContain('runRuntimeApplicationAction');
    expect(source).not.toContain('retryRuntimeApplicationWorkflow');
  });

  test('covers process start, task approval, and closure actions through dispatchAction', () => {
    const processList = read('views/process/instance/list.vue');
    const taskDialog = read('views/process/components/TaskActionDialog.vue');

    expect(processList).toContain('ACTION_TYPES.processInstanceStart');
    expect(processList).toContain('ACTION_TYPES.processClosureEffectRetry');
    expect(processList).toContain('ACTION_TYPES.processClosureEffectMarkHandled');
    expect(processList).toContain('dispatchAction(');
    expect(processList).toContain('requireActionData<ProcessApi.ProcessInstance>');
    expect(processList).not.toContain('startProcessInstance');
    expect(processList).not.toContain('retryClosureEffect');

    expect(taskDialog).toContain('ACTION_TYPES.processTaskApprove');
    expect(taskDialog).toContain('ACTION_TYPES.processTaskReject');
    expect(taskDialog).toContain('ACTION_TYPES.processTaskTransfer');
    expect(taskDialog).toContain('ACTION_TYPES.processTaskAddSign');
    expect(taskDialog).toContain("executionMode: 'SIGNAL'");
    expect(taskDialog).toContain('createActionIdempotencyKey(actionType, task.id)');
    expect(taskDialog).not.toContain('approveTask(');
    expect(taskDialog).not.toContain('rejectTask(');
    expect(taskDialog).not.toContain('transferTask(');
    expect(taskDialog).not.toContain('addSignTask(');
  });

  test('covers integration orchestration and audit lookup by action metadata', () => {
    const workbench = read('views/openapi/workbench/index.vue');
    const audit = read('views/system/audit-log/list.vue');

    expect(workbench).toContain('ACTION_TYPES.integrationOrchestrationStart');
    expect(workbench).toContain("executionMode: 'WORKFLOW'");
    expect(workbench).toContain('ACTION_TARGET_TYPES.integrationRoute');
    expect(workbench).toContain('createActionIdempotencyKey');
    expect(workbench).toContain('dispatchAction');
    expect(workbench).not.toContain('/openapi/runtime/');

    expect(audit).toContain('actionId');
    expect(audit).toContain('actionType');
    expect(audit).toContain('actionSource');
    expect(audit).toContain('actionIdempotencyKey');
    expect(audit).toContain('actionCorrelationId');
  });

  test('covers LUI candidate confirmation and registered component rendering', () => {
    const candidates = read('composables/useActionCandidates.ts');
    const registry = read('registry/action-component-registry.ts');

    expect(candidates).toContain('validateActionCandidate');
    expect(candidates).toContain('dispatchActionCandidate');
    expect(candidates).toContain("input.source ?? 'LUI'");
    expect(candidates).toContain('createActionIdempotencyKey');

    expect(registry).toContain('ActionCandidateConfirmation');
    expect(registry).toContain('ActionResultSummary');
    expect(registry).toContain('ACTION_COMPONENT_NOT_REGISTERED');
    expect(registry).toContain('ACTION_COMPONENT_PROPS_INVALID');
  });
});

function read(relativePath) {
  return readFileSync(`${srcRoot}/${relativePath}`, 'utf8');
}
