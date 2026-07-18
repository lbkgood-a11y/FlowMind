import type { RequestClientOptions } from '@vben/request';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { useAuthStore } from '#/store';

import { refreshTokenApi } from './core';
import { formatApiErrorMessage } from './error-messages';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const AUTH_EXPIRED_CODES = new Set([1004, 1005]);

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
  });
  let isReAuthenticating = false;

  async function doReAuthenticate() {
    if (isReAuthenticating) {
      return;
    }
    isReAuthenticating = true;
    console.warn('Access token or refresh token is invalid or expired.');
    const accessStore = useAccessStore();
    const authStore = useAuthStore();
    accessStore.setAccessToken(null);

    try {
      if (
        preferences.app.loginExpiredMode === 'modal' &&
        accessStore.isAccessChecked
      ) {
        accessStore.setLoginExpired(true);
      } else {
        await authStore.logout();
      }
    } finally {
      isReAuthenticating = false;
    }
  }

  async function doRefreshToken() {
    const accessStore = useAccessStore();
    const response = await refreshTokenApi();
    const result = response.data.data;

    accessStore.setAccessToken(result.accessToken);
    if (result.refreshToken) {
      accessStore.setRefreshToken(result.refreshToken);
    }

    return result.accessToken;
  }

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();

      config.headers.Authorization = formatToken(accessStore.accessToken);
      config.headers['Accept-Language'] = preferences.app.locale;
      return config;
    },
  });

  client.addResponseInterceptor({
    fulfilled: async (response) => {
      const code = response.data?.code;
      if (AUTH_EXPIRED_CODES.has(code)) {
        await doReAuthenticate();
        throw Object.assign({}, response, {
          __skipErrorMessage: true,
          response,
        });
      }
      return response;
    },
  });

  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 0,
    }),
  );

  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate,
      doRefreshToken,
      enableRefreshToken: preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      if (error?.__skipErrorMessage) {
        return;
      }
      const responseData = error?.response?.data ?? error ?? {};
      const errorMessage = responseData?.error ?? responseData?.message ?? '';
      message.error(
        formatApiErrorMessage(errorMessage || msg, responseData?.data) ||
          errorMessage ||
          msg,
      );
    }),
  );

  return client;
}

export const requestClient = createRequestClient(apiURL, {
  responseReturn: 'data',
});

export const baseRequestClient = new RequestClient({ baseURL: apiURL });
