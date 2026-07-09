<script lang="ts" setup>
import { AuthenticationQrCodeLogin } from '@vben/common-ui';
import { LOGIN_PATH } from '@vben/constants';

defineOptions({ name: 'QrCodeLogin' });
</script>

<template>
  <AuthenticationQrCodeLogin
    description="扫码登录后端接口尚未接入，请使用账号密码登录。"
    :login-path="LOGIN_PATH"
  />
</template>
