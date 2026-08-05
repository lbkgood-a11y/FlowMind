import { describe, expect, it } from 'vitest';

import {
  canEnableChannel,
  CONFIGURATION_RESPONSIVE_BREAKPOINT,
  hasConfigurationPermission,
  safeCredentialLabel,
} from './configuration-policies';

describe('notification configuration presentation policies', () => {
  it.each(['NOT_CONNECTED', 'INVALID', 'DISABLED', 'DEGRADED'] as const)(
    'does not enable unavailable state %s',
    (capabilityState) => {
      expect(canEnableChannel({ capabilityState })).toBe(false);
    },
  );

  it('only enables READY and never invents a credential value', () => {
    expect(canEnableChannel({ capabilityState: 'READY' })).toBe(true);
    expect(safeCredentialLabel({ credentialConfigured: true })).toBe(
      '已配置（受保护）',
    );
    expect(
      safeCredentialLabel({
        credentialConfigured: false,
        maskedCredentialReference: 'secret',
      }),
    ).toBe('未配置');
  });

  it('declares the compact layout breakpoint', () => {
    expect(CONFIGURATION_RESPONSIVE_BREAKPOINT).toBe(768);
  });

  it('defaults management controls to hidden without explicit permission', () => {
    expect(
      hasConfigurationPermission(
        () => false,
        '/api/v2/notification-channels/**:PUT',
      ),
    ).toBe(false);
    expect(
      hasConfigurationPermission(
        (codes) => codes.includes('/api/v2/notification-channels/**:PUT'),
        '/api/v2/notification-channels/**:PUT',
      ),
    ).toBe(true);
  });
});
