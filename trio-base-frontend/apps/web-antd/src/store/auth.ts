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
  let permissionRefreshTimer: ReturnType<typeof setInterval> | null = null;

  function resetAccessState() {
    resetStaticRoutes(router, routes);
    accessStore.setAccessCodes([]);
    accessStore.setIsAccessChecked(false);
    accessStore.setAccessMenus([]);
    accessStore.setAccessRoutes([]);
  }

  async function completeAuthentication(
    result: { accessToken: string; refreshToken?: string; permissions?: string[] },
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

    const accessCodes = result.permissions
      ? result.permissions
      : await getAccessCodesApi();

    const fetchUserInfoResult = await fetchUserInfo();
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

    startPermissionRefresh();

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
    const currentPath = router.currentRoute.value.fullPath;
    const loginHref = router.resolve({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(currentPath),
          }
        : {},
    }).href;

    try {
      await logoutApi();
    } catch {
      // Ignore logout API failures and clear local state anyway.
    } finally {
      stopPermissionRefresh();
      try {
        // 显式清空持久化 Token，避免页面刷新后登录守卫再次恢复旧会话。
        accessStore.setAccessToken(null);
        accessStore.setRefreshToken(null);

        // 清除 Vue Router 中的旧动态路由和访问状态。
        resetAccessState();
        resetAllStores();
        accessStore.setAccessToken(null);
        accessStore.setRefreshToken(null);
        accessStore.setLoginExpired(false);
        accessStore.setIsAccessChecked(false);
      } finally {
        // 即使某个 Store 重置失败，也必须离开受保护页面。
        window.location.replace(loginHref);
      }
    }
  }

  async function fetchUserInfo() {
    const userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
    return userInfo;
  }

  function startPermissionRefresh(intervalMs = 300_000) {
    stopPermissionRefresh();
    permissionRefreshTimer = setInterval(async () => {
      try {
        const codes = await getAccessCodesApi();
        accessStore.setAccessCodes(codes);
      } catch {
        // Silently skip — token refresh handler will pick up expired sessions
      }
    }, intervalMs);
  }

  function stopPermissionRefresh() {
    if (permissionRefreshTimer !== null) {
      clearInterval(permissionRefreshTimer);
      permissionRefreshTimer = null;
    }
  }

  function $reset() {
    loginLoading.value = false;
    registerLoading.value = false;
    stopPermissionRefresh();
  }

  return {
    $reset,
    authLogin,
    authRegister,
    fetchUserInfo,
    loginLoading,
    logout,
    registerLoading,
    startPermissionRefresh,
    stopPermissionRefresh,
  };
});
