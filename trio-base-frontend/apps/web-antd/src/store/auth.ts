import type { MenuRecordRaw, Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';
import { resetStaticRoutes } from '@vben/utils';

import { notification } from 'ant-design-vue';
import { defineStore } from 'pinia';

import { generateAccess } from '../router/access';
import { accessRoutes, routes } from '../router/routes';

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
  let permissionRefreshTimer: ReturnType<typeof setTimeout> | null = null;
  let permissionRefreshFailures = 0;
  const MAX_PERMISSION_REFRESH_BACKOFF_MS = 1_800_000; // 30 min max

  function resetAccessState() {
    resetStaticRoutes(router, routes);
    accessStore.setAccessCodes([]);
    accessStore.setIsAccessChecked(false);
    accessStore.setAccessMenus([]);
    accessStore.setAccessRoutes([]);
  }

  function sameAccessCodes(left: string[], right: string[]) {
    if (left.length !== right.length) {
      return false;
    }
    const expected = new Set(left);
    return right.every((code) => expected.has(code));
  }

  function firstAccessibleMenuPath(menus: MenuRecordRaw[]): string | undefined {
    for (const menu of menus) {
      const childPath = menu.children?.length
        ? firstAccessibleMenuPath(menu.children)
        : undefined;
      if (childPath) {
        return childPath;
      }
      if (menu.path && !menu.disabled) {
        return menu.path;
      }
    }
    return undefined;
  }

  async function rebuildAccessProjection(codes: string[]) {
    const currentPath = router.currentRoute.value.fullPath;
    resetAccessState();
    accessStore.setAccessCodes(codes);

    const userInfo = userStore.userInfo || (await fetchUserInfo());
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: userInfo.roles ?? [],
      router,
      routes: accessRoutes,
    });
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);

    const resolvedCurrent = router.resolve(currentPath);
    const targetPath =
      resolvedCurrent.name === 'FallbackNotFound'
        ? firstAccessibleMenuPath(accessibleMenus) ||
          userInfo.homePath ||
          preferences.app.defaultHomePath
        : currentPath;
    await router.replace(targetPath);
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
    permissionRefreshFailures = 0;
    scheduleRefresh(intervalMs);
  }

  function scheduleRefresh(delayMs: number) {
    permissionRefreshTimer = setTimeout(async () => {
      try {
        const codes = await getAccessCodesApi();
        if (!sameAccessCodes(accessStore.accessCodes, codes)) {
          await rebuildAccessProjection(codes);
        }
        permissionRefreshFailures = 0;
        scheduleRefresh(300_000);
      } catch (err) {
        permissionRefreshFailures++;
        const backoff = Math.min(
          300_000 * 2 ** permissionRefreshFailures,
          MAX_PERMISSION_REFRESH_BACKOFF_MS,
        );
        console.warn(
          `[Auth] Permission refresh failed (#${permissionRefreshFailures}), retrying in ${Math.round(backoff / 1000)}s`,
          err,
        );
        scheduleRefresh(backoff);
      }
    }, delayMs);
  }

  function stopPermissionRefresh() {
    if (permissionRefreshTimer !== null) {
      clearTimeout(permissionRefreshTimer);
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
