import { describe, expect, it } from 'vitest';

import { defaultListDesign, validateRelationDepth } from './application-designer';

describe('lowcode application designer', () => {
  it('builds safe declarative list metadata', () => {
    const design = defaultListDesign([{ fieldKey: 'applicant', fieldType: 'string', label: '申请人' }]);
    expect(design.columns[0]).toMatchObject({ fieldKey: 'applicant', format: 'text' });
    expect(design.pageSize).toBe(20);
  });

  it('accepts master-child-grandchild and rejects fourth level', () => {
    const relation = (parent: string, child: string) => ({
      cardinality: 'MANY' as const,
      childForeignKeyField: 'parentId',
      childFormDefinitionId: child,
      parentFormDefinitionId: parent,
      parentKeyField: 'id',
      relationCode: `${parent}_${child}`,
    });
    expect(validateRelationDepth('A', [relation('A', 'B'), relation('B', 'C')])).toBeUndefined();
    expect(validateRelationDepth('A', [relation('A', 'B'), relation('B', 'C'), relation('C', 'D')])).toContain('三级');
  });
});
