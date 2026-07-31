import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import AuthorizationStageNavigation from './AuthorizationStageNavigation.vue';
import FieldEnforcementCapabilities from './FieldEnforcementCapabilities.vue';

describe('role authorization workbench components', () => {
  it('renders stages in implementation order and emits navigation', async () => {
    const wrapper = mount(AuthorizationStageNavigation, {
      props: { activeKey: 'function', completedKeys: ['function'] },
    });

    expect(wrapper.text()).toContain('功能与菜单');
    expect(wrapper.text()).toContain('数据范围');
    expect(wrapper.text()).toContain('字段访问');
    expect(wrapper.text()).toContain('业务约束');
    expect(wrapper.text()).toContain('验证');
  });

  it('shows verified and unsupported field capabilities consistently', () => {
    const wrapper = mount(FieldEnforcementCapabilities, {
      props: { readHide: true, readMask: false, writeDeny: true },
    });

    const tags = wrapper.findAll('.ant-tag');
    expect(tags.map((tag) => tag.text())).toEqual([
      '读取隐藏',
      '读取脱敏',
      '写入拒绝',
    ]);
    expect(tags[0]?.classes()).toContain('ant-tag-success');
    expect(tags[1]?.classes()).not.toContain('ant-tag-success');
  });
});
