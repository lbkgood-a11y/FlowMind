import type { NotificationConfigurationApi as Api } from '#/api/notification-configuration';

export const CONFIGURATION_RESPONSIVE_BREAKPOINT = 768;

export function hasConfigurationPermission(
  check: (codes: string[]) => boolean,
  code: string,
) {
  return check([code]);
}

export function canEnableChannel(
  channel: Pick<Api.Channel, 'capabilityState'>,
) {
  return channel.capabilityState === 'READY';
}

export function safeCredentialLabel(
  provider: Pick<
    Api.Provider,
    'credentialConfigured' | 'maskedCredentialReference'
  >,
) {
  if (!provider.credentialConfigured) return '未配置';
  return provider.maskedCredentialReference || '已配置（受保护）';
}
