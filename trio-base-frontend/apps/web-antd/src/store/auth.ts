import type { Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';
import { resetStaticRoutes } from '@vben/utils';

import { notification } from 'ant-design-vue';
import { defineStore } from 'pinia';

import { routes } from '../router/routes';

import {
  getAccessCodesApi,
  getUserInfoApi,
  loginApi,
  logoutApi,
  registerApi,
} from '#/api';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);
  const registerLoading = ref(false);

  function resetAccessState() {
    resetStaticRoutes(router, routes);
    accessStore.setAccessCodes([]);
    accessStore.setIsAccessChecked(false);
    accessStore.setAccessMenus([]);
    accessStore.setAccessRoutes([]);
  }

  async function completeAuthentication(
    result: { accessToken: string; refreshToken?: string },
    onSuccess?: () => Promise<void> | void,
  ) {
    let userInfo: null | UserInfo = null;

    if (!result.accessToken) {
      return { userInfo };
    }

    accessStore.setAccessToken(result.accessToken);
    if (result.refreshToken) {
      accessStore.setRefreshToken(result.refreshToken);
    }

    const [fetchUserInfoResult, accessCodes] = await Promise.all([
      fetchUserInfo(),
      getAccessCodesApi(),
    ]);

    userInfo = fetchUserInfoResult;
    userStore.setUserInfo(userInfo);
    accessStore.setAccessCodes(accessCodes);

    if (accessStore.loginExpired) {
      accessStore.setLoginExpired(false);
    } else {
      await (onSuccess
        ? onSuccess()
        : router.push(userInfo.homePath || preferences.app.defaultHomePath));
    }

    if (userInfo?.realName) {
      notification.success({
        description: `${$t('authentication.loginSuccessDesc')}:${userInfo.realName}`,
        duration: 3,
        message: $t('authentication.loginSuccess'),
      });
    }

    return { userInfo };
  }

  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    try {
      loginLoading.value = true;
      // 清除旧动态路由和访问检查标记，确保每次登录都重新生成路由
      // （防止用户未退出直接切换账号登录时，isAccessChecked 仍然为 true）
      resetAccessState();

      const result = await loginApi({
        password: params.password,
        username: params.username,
      });

      return await completeAuthentication(result, onSuccess);
    } finally {
      loginLoading.value = false;
    }
  }

  async function authRegister(params: Recordable<any>) {
    try {
      registerLoading.value = true;
      // 清除旧动态路由，防止注册登录后残留旧用户路由
      resetAccessState();

      const result = await registerApi({
        email: params.email,
        password: params.password,
        phone: params.phone,
        username: params.username,
      });

      notification.success({
        description: params.username,
        duration: 3,
        message: $t('authentication.signUp'),
      });

      return await completeAuthentication(result);
    } finally {
      registerLoading.value = false;
    }
  }

  async function logout(redirect: boolean = true) {
    try {
      await logoutApi();
    } catch {
      // Ignore logout API failures and clear local state anyway.
    }

    // 清除 Vue Router 中的旧动态路由，防止下次登录时残留旧用户的路由和菜单
    resetAccessState();

    // 重置所有 Pinia store，包括 isAccessChecked，确保下次登录重新生成路由
    resetAllStores();
    accessStore.setLoginExpired(false);

    // 强制清除访问检查标记，确保访问守卫重新执行
    accessStore.setIsAccessChecked(false);

    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  async function fetchUserInfo() {
    const userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
    return userInfo;
  }

  function $reset() {
    loginLoading.value = false;
    registerLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    authRegister,
    fetchUserInfo,
    loginLoading,
    logout,
    registerLoading,
  };
});
