import { requestClient } from '#/api/request';

export namespace NotificationConfigurationApi {
  export type ChannelCode = 'DINGTALK' | 'EMAIL' | 'IN_APP' | 'SMS' | 'WE_COM';
  export type CapabilityState =
    | 'DEGRADED'
    | 'DISABLED'
    | 'INVALID'
    | 'NOT_CONNECTED'
    | 'READY';
  export interface Channel {
    adapterKey?: string;
    adapterVersion?: string;
    capabilityState: CapabilityState;
    channelCode: ChannelCode;
    desiredEnabled: number;
    validatedAt?: string;
    validationSummary?: string;
  }
  export interface Provider {
    channelCode: ChannelCode;
    credentialConfigured: boolean;
    displayName: string;
    enabled: boolean;
    maskedCredentialReference?: string;
    providerKey: string;
    settings: Record<string, string>;
  }
  export interface Template {
    bodyTemplate: string;
    channelCode: ChannelCode;
    effectiveFrom?: string;
    effectiveUntil?: string;
    localeCode: string;
    state: string;
    subjectTemplate?: string;
    templateId: string;
    templateKey: string;
    variableSchema: Record<string, string>;
    versionId: string;
    versionNo: number;
  }
  export interface RoutingPolicy {
    categoryCode: string;
    enabled: number;
    fallbackEnabled: number;
    mandatoryCategory: number;
    orderedChannels: string;
    priorityCode: string;
    quietHoursJson?: string;
  }
}

export const getNotificationChannels = () =>
  requestClient.get<NotificationConfigurationApi.Channel[]>(
    '/v2/notification-channels',
  );
export const getNotificationProviders = () =>
  requestClient.get<NotificationConfigurationApi.Provider[]>(
    '/v2/notification-channels/providers',
  );
export const saveNotificationProvider = (data: Record<string, unknown>) =>
  requestClient.put('/v2/notification-channels/providers', data);
export const validateNotificationChannel = (
  channelCode: string,
  providerKey?: string,
) =>
  requestClient.put(
    `/v2/notification-channels/${channelCode}/validate`,
    undefined,
    { params: { providerKey } },
  );
export const setNotificationChannelEnabled = (
  channelCode: string,
  enabled: boolean,
) =>
  requestClient.put(
    `/v2/notification-channels/${channelCode}/enabled`,
    undefined,
    { params: { enabled } },
  );
export const getNotificationTemplates = () =>
  requestClient.get<NotificationConfigurationApi.Template[]>(
    '/v2/notification-templates',
  );
export const createNotificationTemplate = (data: Record<string, unknown>) =>
  requestClient.post('/v2/notification-templates', data);
export const transitionNotificationTemplate = (
  versionId: string,
  command: 'publish' | 'reject' | 'submit-review',
) => requestClient.put(`/v2/notification-templates/${versionId}/${command}`);
export const getNotificationRoutingPolicies = () =>
  requestClient.get<NotificationConfigurationApi.RoutingPolicy[]>(
    '/v2/notification-channels/routing-policies',
  );
export const saveNotificationRoutingPolicy = (data: Record<string, unknown>) =>
  requestClient.put('/v2/notification-channels/routing-policies', data);
