<script setup lang="ts">
import type { SystemRoleApi } from '#/api/system/role';

import { reactive, ref, watch } from 'vue';

import {
  Button,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Radio,
  RadioGroup,
  Space,
  Tag,
} from 'ant-design-vue';

import { createRole, roleCodeExists, updateRole } from '#/api/system/role';

const props = defineProps<{
  canCreate: boolean;
  canUpdate: boolean;
  role?: SystemRoleApi.SystemRole;
}>();
const emit = defineEmits<{
  saved: [roleId: string];
}>();

const drawerOpen = ref(false);
const saving = ref(false);
const editing = ref(false);
const formModel = reactive<SystemRoleApi.SaveRoleParams>({
  description: '',
  roleCode: '',
  roleName: '',
  status: 1,
});

function resetForm(role?: SystemRoleApi.SystemRole) {
  editing.value = !!role;
  formModel.roleCode = role?.roleCode ?? '';
  formModel.roleName = role?.roleName ?? '';
  formModel.description = role?.description ?? '';
  formModel.status = role?.status ?? 1;
}

function openCreate() {
  if (!props.canCreate) return;
  resetForm();
  drawerOpen.value = true;
}

function openEdit() {
  if (!props.role || !props.canUpdate) return;
  resetForm(props.role);
  drawerOpen.value = true;
}

async function submit() {
  const roleCode = formModel.roleCode?.trim() ?? '';
  const roleName = formModel.roleName.trim();
  if (!roleCode || !roleName) {
    message.warning('请填写角色编码和角色名称');
    return;
  }
  saving.value = true;
  try {
    if (!editing.value && (await roleCodeExists(roleCode))) {
      message.warning('角色编码已存在');
      return;
    }
    const payload: SystemRoleApi.SaveRoleParams = {
      description: formModel.description?.trim() || undefined,
      roleName,
      status: formModel.status,
    };
    let saved: SystemRoleApi.SystemRole;
    if (editing.value && props.role) {
      saved = await updateRole(props.role.id, payload);
      message.success('角色基本信息已更新');
    } else {
      saved = await createRole({ ...payload, roleCode });
      message.success('角色已创建');
    }
    drawerOpen.value = false;
    emit('saved', saved.id);
  } finally {
    saving.value = false;
  }
}

watch(() => props.role, (role) => {
  if (drawerOpen.value && editing.value) resetForm(role);
});

defineExpose({ openCreate, openEdit });
</script>

<template>
  <section class="role-basic-panel">
    <template v-if="role">
      <div class="section-heading">
        <div>
          <h3>角色基本信息</h3>
          <p>维护角色标识、名称、状态和职责说明。</p>
        </div>
        <Button v-if="canUpdate" type="primary" @click="openEdit">编辑基本信息</Button>
      </div>
      <Descriptions bordered :column="2" size="small">
        <DescriptionsItem label="角色编码"><code>{{ role.roleCode }}</code></DescriptionsItem>
        <DescriptionsItem label="角色名称">{{ role.roleName }}</DescriptionsItem>
        <DescriptionsItem label="状态"><Tag :color="role.status === 0 ? 'default' : 'success'">{{ role.status === 0 ? '停用' : '启用' }}</Tag></DescriptionsItem>
        <DescriptionsItem label="创建时间">{{ role.createdAt || '-' }}</DescriptionsItem>
        <DescriptionsItem label="职责说明" :span="2">{{ role.description || '暂无说明' }}</DescriptionsItem>
      </Descriptions>
    </template>
    <Empty v-else description="请从左侧选择角色">
      <Button v-if="canCreate" type="primary" @click="openCreate">新建角色</Button>
    </Empty>

    <Drawer v-model:open="drawerOpen" :title="editing ? '编辑角色基本信息' : '新建角色'" width="560">
      <Form :model="formModel" layout="vertical">
        <FormItem label="角色编码" required>
          <Input v-model:value="formModel.roleCode" :disabled="editing" placeholder="如 DEPARTMENT_MANAGER" />
        </FormItem>
        <FormItem label="角色名称" required>
          <Input v-model:value="formModel.roleName" placeholder="请输入角色名称" />
        </FormItem>
        <FormItem label="状态">
          <RadioGroup v-model:value="formModel.status" button-style="solid">
            <Radio :value="1">启用</Radio><Radio :value="0">停用</Radio>
          </RadioGroup>
        </FormItem>
        <FormItem label="职责说明">
          <Input.TextArea v-model:value="formModel.description" :rows="5" placeholder="说明该角色的职责与适用范围" />
        </FormItem>
      </Form>
      <template #footer><Space><Button @click="drawerOpen = false">取消</Button><Button type="primary" :loading="saving" @click="submit">保存</Button></Space></template>
    </Drawer>
  </section>
</template>

<style scoped>
.role-basic-panel{display:flex;flex:1;min-height:0;flex-direction:column;padding:16px;overflow:auto;background:#fff}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px;padding-bottom:12px;border-bottom:1px solid #edf0f5}.section-heading h3{margin:0;font-size:16px;font-weight:600}.section-heading p{margin:4px 0 0;color:#6b7280}
@media(max-width:720px){.role-basic-panel{padding:12px}.section-heading{gap:12px}.role-basic-panel :deep(.ant-descriptions-row){display:flex;flex-direction:column}}
</style>
