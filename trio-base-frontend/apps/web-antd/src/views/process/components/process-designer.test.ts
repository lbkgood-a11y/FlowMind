import { describe, expect, it } from 'vitest';

import {
  buildParticipantAssignment,
  validateProcessDefinition,
} from './process-designer';

describe('process designer validation', () => {
  it('builds ROLE, USER, and DEPT participant payloads', () => {
    expect(buildParticipantAssignment('ROLE', 'DEPT_HEAD')).toEqual({
      roleCode: 'DEPT_HEAD',
      type: 'ROLE',
    });
    expect(buildParticipantAssignment('USER', 'USER001')).toEqual({
      type: 'USER',
      userId: 'USER001',
    });
    expect(buildParticipantAssignment('DEPT', 'FINANCE', 'ADMIN')).toEqual({
      deptCode: 'FINANCE',
      dimensionCode: 'ADMIN',
      type: 'DEPT',
    });
  });

  it('blocks unsupported nodes and missing participants before publish', () => {
    const errors = validateProcessDefinition(
      JSON.stringify({
        flow: {
          nodes: [
            {
              id: 'start',
              next: [{ condition: 'true', target: 'approve' }],
              type: 'START',
            },
            {
              id: 'approve',
              next: [{ condition: 'true', target: 'service' }],
              type: 'APPROVAL',
            },
            {
              id: 'service',
              next: [{ condition: 'true', target: 'end' }],
              type: 'SERVICE_TASK',
            },
            { id: 'end', type: 'END' },
          ],
        },
      }),
    );
    expect(errors.some((error) => error.includes('缺少有效参与者'))).toBe(true);
    expect(errors.some((error) => error.includes('未支持类型'))).toBe(true);
  });
});
