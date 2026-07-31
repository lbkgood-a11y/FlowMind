import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  getOrCreateRoleAuthorizationDraft: vi.fn(),
  getOrgDimensions: vi.fn(),
  getOrgTree: vi.fn(),
  getPageCapabilities: vi.fn(),
  getRoleAuthorizationReleases: vi.fn(),
  getRoleAuthorizationDrifts: vi.fn(),
  getUserList: vi.fn(),
  publishRoleAuthorizationDraft: vi.fn(),
  replaceRoleCapabilityIntent: vi.fn(),
  rollbackRoleAuthorizationRelease: vi.fn(),
  simulatePageCapability: vi.fn(),
  validateRoleAuthorizationDraft: vi.fn(),
}));

vi.mock('#/api', () => api);

import PageCapabilityAuthorizationWorkbench from './PageCapabilityAuthorizationWorkbench.vue';

describe('page capability authorization workbench', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getPageCapabilities.mockResolvedValue([
      {
        category: 'ACCESS', capabilityName: '进入用户管理', id: 'access', pageCode: 'USER',
        pageName: '用户管理', readiness: 'READY', requiredCapabilityIds: [],
      },
      {
        category: 'READ', capabilityName: '查看用户列表', id: 'read', pageCode: 'USER',
        pageName: '用户管理', readiness: 'READY', requiredCapabilityIds: ['access'], scopeConfigurable: true,
      },
      {
        category: 'OPERATION', capabilityName: '新增用户', id: 'create', pageCode: 'USER',
        pageName: '用户管理', readiness: 'READY', requiredCapabilityIds: ['read'], scopeConfigurable: true,
      },
    ]);
    api.getOrCreateRoleAuthorizationDraft.mockResolvedValue({
      catalogId: 'catalog-1', draftId: 'draft-1', roleId: 'role-1', selections: [], status: 'DRAFT', version: 1,
    });
    api.getRoleAuthorizationReleases.mockResolvedValue([{
      businessSummary: '用户管理：只允许查看', catalogVersion: 1, intentVersion: 1,
      publishedAt: '2026-07-29T10:00:00', publishedBy: 'admin', releaseId: 'release-1',
      releaseNumber: 1, roleId: 'role-1',
    }]);
    api.getRoleAuthorizationDrifts.mockResolvedValue([]);
    api.getOrgDimensions.mockResolvedValue([]);
    api.getOrgTree.mockResolvedValue([]);
    api.getUserList.mockResolvedValue({ items: [], total: 0 });
    api.replaceRoleCapabilityIntent.mockImplementation(async (_draftId, request) => ({
      catalogId: 'catalog-1', draftId: 'draft-1', roleId: 'role-1',
      selections: request.selections, status: 'DRAFT', version: 2,
    }));
    api.validateRoleAuthorizationDraft.mockResolvedValue({
      affectedUserCount: 3, blockingErrors: [], businessSummary: '用户管理：进入页面、查看用户列表、新增用户',
      compilation: { dataPolicies: [], fieldPolicies: [], grants: [{}, {}, {}], guards: [] },
      expiresAt: '2026-07-30T18:00:00', validationToken: 'token', warnings: [],
    });
    api.publishRoleAuthorizationDraft.mockResolvedValue({ releaseId: 'release-2' });
    api.rollbackRoleAuthorizationRelease.mockResolvedValue({ releaseId: 'release-1' });
  });

  it('uses business language and automatically closes operation dependencies', async () => {
    const wrapper = mount(PageCapabilityAuthorizationWorkbench, {
      props: { canManage: true, roleId: 'role-1' },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('允许进入页面');
    expect(wrapper.text()).toContain('允许查看');
    expect(wrapper.text()).toContain('允许操作');
    expect(wrapper.text()).not.toContain('资源动作');

    const checkboxes = wrapper.findAll('input[type="checkbox"]');
    await checkboxes[2]?.setValue(true);
    await flushPromises();
    expect(wrapper.text()).toContain('系统自动添加');

    const validateButton = wrapper.findAll('button').find((item) => item.text().includes('校验权限'));
    await validateButton?.trigger('click');
    await flushPromises();

    expect(api.replaceRoleCapabilityIntent).toHaveBeenCalledWith(
      'draft-1',
      expect.objectContaining({
        selections: expect.arrayContaining([
          expect.objectContaining({ capabilityId: 'access' }),
          expect.objectContaining({ capabilityId: 'read' }),
          expect.objectContaining({ capabilityId: 'create' }),
        ]),
      }),
    );
    expect(api.validateRoleAuthorizationDraft).toHaveBeenCalledWith('draft-1', { expectedVersion: 2 });
    expect(wrapper.text()).toContain('草稿不会影响线上用户');

    const publishButton = wrapper.findAll('button').find((item) => item.text().includes('确认并发布'));
    await publishButton?.trigger('click');
    await flushPromises();
    (document.querySelector('.ant-modal-confirm-btns .ant-btn-primary') as HTMLButtonElement)?.click();
    await flushPromises();
    expect(api.publishRoleAuthorizationDraft).toHaveBeenCalledWith('draft-1', {
      expectedVersion: 2,
      validationToken: 'token',
    });

    const rollbackButton = wrapper.findAll('button').find((item) => item.text().includes('恢复此版本'));
    await rollbackButton?.trigger('click');
    await flushPromises();
    const confirmations = document.querySelectorAll('.ant-modal-confirm-btns .ant-btn-primary');
    (confirmations[confirmations.length - 1] as HTMLButtonElement)?.click();
    await flushPromises();
    expect(api.rollbackRoleAuthorizationRelease).toHaveBeenCalledWith('role-1', 'release-1');
  });
});
