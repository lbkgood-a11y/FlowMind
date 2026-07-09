import type { HttpResponse } from '@vben/request';

import { useAccessStore } from '@vben/stores';

import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  export interface LoginParams {
    password: string;
    username: string;
  }

  export interface RegisterParams {
    email?: string;
    password: string;
    phone?: string;
    username: string;
  }

  export interface LoginResult {
    accessToken: string;
    expiresIn?: number;
    refreshToken?: string;
    roles?: string[];
    userId?: string;
    username?: string;
  }
}

export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/login', data);
}

export async function registerApi(data: AuthApi.RegisterParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/register', undefined, {
    params: data,
  });
}

export async function refreshTokenApi() {
  const accessStore = useAccessStore();
  return baseRequestClient.post<{ data: HttpResponse<AuthApi.LoginResult> }>(
    '/auth/refresh',
    undefined,
    {
      params: {
        refreshToken: accessStore.refreshToken,
      },
    },
  );
}

export async function logoutApi() {
  const accessStore = useAccessStore();
  return baseRequestClient.post('/auth/logout', {
    accessToken: accessStore.accessToken,
    refreshToken: accessStore.refreshToken,
  });
}

export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}
