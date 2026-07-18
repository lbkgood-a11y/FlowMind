import type { PayloadFormDefinition, PayloadFormField } from './payload-form';

import { describe, expect, it } from 'vitest';

import {
  buildPayload,
  createArrayItemState,
  createPayloadFormState,
  parsePayloadJson,
  PayloadFormError,
  payloadToFormState,
} from './payload-form';
import { lifecycleResources } from './resource-config';

describe('openapi lifecycle visual payload forms', () => {
  it('provides create metadata and payload generation for every lifecycle resource', () => {
    expect(Object.keys(lifecycleResources).sort()).toEqual([
      'applications',
      'callbacks',
      'connectors',
      'mappings',
      'orchestrations',
      'policies',
      'products',
      'routes',
      'structures',
      'subscriptions',
      'value-maps',
    ]);

    for (const resource of Object.values(lifecycleResources)) {
      expect(resource.createEndpoint).toMatch(/^\/openapi\/management\//);
      expect(resource.createForm.groups.length).toBeGreaterThan(0);
      expect(() =>
        buildPayload(resource.createForm, createPayloadFormState(resource.createForm)),
      ).not.toThrow();
    }
  });

  it('generates connector payloads with a nested network policy object', () => {
    const form = resource('connectors').createForm;
    const state = createPayloadFormState(form);
    Object.assign(state, {
      baseUrl: 'https://api.partner.example',
      connectorKey: 'partner-order-create',
      displayName: '伙伴订单创建',
      operationPath: '/v1/orders',
      ownerId: 'owner-1',
    });
    state.networkPolicy = {
      ...(state.networkPolicy as Record<string, unknown>),
      allowedHosts: ['api.partner.example'],
    };

    const payload = buildPayload(form, state);

    expect(payload.networkPolicy).toEqual({
      allowPrivateNetwork: false,
      allowedHosts: ['api.partner.example'],
    });
    expect(payload.timeoutMillis).toBe(3000);
    expect(payload.responseSizeLimit).toBe(1_048_576);
  });

  it('builds mapping rules from nested visual rows and parses rule JSON config', () => {
    const form = resource('mappings').createForm;
    const state = createPayloadFormState(form);
    Object.assign(state, {
      canonicalStructureId: 'canonical-structure',
      displayName: '订单映射',
      externalStructureId: 'external-structure',
      mappingKey: 'order-map',
      ownerId: 'owner-1',
      sourceStructureVersionId: 'source-version',
      targetStructureVersionId: 'target-version',
    });
    const firstRule = (state.rules as Record<string, unknown>[])[0]!;
    firstRule.config = '{"trim":true}';

    const payload = buildPayload(form, state);

    expect(payload.rules).toEqual([
      {
        config: { trim: true },
        operation: 'COPY',
        order: 1,
        required: true,
        sourcePointer: '/id',
        targetPointer: '/id',
      },
    ]);
  });

  it('supports adding product route rows with array-backed JSON node fields', () => {
    const form = resource('products').createForm;
    const state = createPayloadFormState(form);
    const routesField = findField(form, 'routes');
    const row = createArrayItemState(routesField);
    Object.assign(state, {
      displayName: '订单开放 API',
      ownerId: 'owner-1',
      productKey: 'order-openapi',
    });
    Object.assign(row, {
      canonicalStructureVersionIds: ['structure-version'],
      operations: ['createOrder'],
      releaseSnapshotId: 'release-1',
      routeKey: 'partner-order-create',
      scopes: ['orders:write'],
    });
    state.routes = [row];

    const payload = buildPayload(form, state);

    expect(payload.routes).toEqual([
      {
        canonicalStructureVersionIds: ['structure-version'],
        operations: ['createOrder'],
        releaseSnapshotId: 'release-1',
        routeKey: 'partner-order-create',
        scopes: ['orders:write'],
      },
    ]);
  });

  it('validates invalid JSON fallback and visual JSON fields before submit', () => {
    expect(() => parsePayloadJson('{bad json')).toThrow(PayloadFormError);

    const form = resource('mappings').createForm;
    const state = createPayloadFormState(form);
    const firstRule = (state.rules as Record<string, unknown>[])[0]!;
    firstRule.config = '{bad json';

    expect(() => buildPayload(form, state)).toThrow('规则配置 不是合法 JSON');
  });

  it('round-trips advanced JSON payloads back into visual form state', () => {
    const form = resource('connectors').createForm;
    const payload = parsePayloadJson(`{
      "connectorKey": "partner-order-create",
      "displayName": "伙伴订单创建",
      "ownerId": "owner-1",
      "baseUrl": "https://api.partner.example",
      "operationPath": "/v1/orders",
      "httpMethod": "POST",
      "timeoutMillis": 5000,
      "operationClass": "STATE_CHANGING",
      "authenticationType": "NONE",
      "networkPolicy": { "allowedHosts": ["api.partner.example"] },
      "responseSizeLimit": 1048576
    }`);

    const state = payloadToFormState(form, payload);

    expect(state.connectorKey).toBe('partner-order-create');
    expect(state.networkPolicy).toEqual({
      allowPrivateNetwork: false,
      allowedHosts: ['api.partner.example'],
    });
    expect(buildPayload(form, state).timeoutMillis).toBe(5000);
  });

  it('marks bodyless actions and configures forms for body-based lifecycle actions', () => {
    expect(
      resource('applications').actions.find((item) => item.label === '创建客户端')?.form,
    ).toBeTruthy();
    expect(
      resource('applications').actions.find((item) => item.label === '轮换客户端凭证')?.form,
    ).toBeTruthy();
    expect(
      resource('routes').actions.find((item) => item.label === '创建 Release')?.form,
    ).toBeTruthy();
    expect(
      resource('routes').actions.find((item) => item.label === '回滚路由')?.form,
    ).toBeTruthy();
    expect(
      resource('subscriptions').actions.find((item) => item.label === '升级订阅')?.form,
    ).toBeTruthy();
    expect(
      resource('mappings').actions.find((item) => item.label === '预览转换')?.form,
    ).toBeTruthy();
    expect(
      resource('value-maps').actions.find((item) => item.label === '值查找')?.form,
    ).toBeTruthy();
    expect(
      resource('products').actions.find((item) => item.label === '发布产品版本')?.bodyless,
    ).toBe(true);
  });

  it('lists versioned resources and provides draft edit forms where mutable', () => {
    expect(resource('structures')).toMatchObject({
      assetType: 'structure-versions',
      edit: { path: '/openapi/management/structures/versions/{id}' },
    });
    expect(resource('mappings')).toMatchObject({
      assetType: 'mapping-versions',
      edit: { path: '/openapi/management/mappings/versions/{id}/rules' },
    });
    expect(resource('value-maps')).toMatchObject({
      assetType: 'value-map-versions',
      edit: { path: '/openapi/management/value-maps/versions/{id}' },
    });
    expect(resource('connectors')).toMatchObject({
      assetType: 'connector-versions',
      edit: { path: '/openapi/management/connectors/versions/{id}' },
    });
    expect(resource('routes')).toMatchObject({
      assetType: 'route-versions',
      edit: { path: '/openapi/management/routes/versions/{id}' },
    });
    expect(resource('products')).toMatchObject({
      assetType: 'product-versions',
      edit: { path: '/openapi/management/products/versions/{id}' },
    });
    expect(resource('applications').edit).toMatchObject({
      path: '/openapi/management/applications/{id}',
      states: ['DRAFT', 'PENDING_APPROVAL'],
    });
    expect(resource('policies').edit).toMatchObject({
      path: '/openapi/management/policies/{id}',
      states: ['DRAFT'],
    });

    for (const key of [
      'applications',
      'callbacks',
      'connectors',
      'mappings',
      'orchestrations',
      'policies',
      'products',
      'routes',
      'structures',
      'value-maps',
    ]) {
      const edit = resource(key).edit;
      expect(edit, `${key} should have a visual edit config`).toBeTruthy();
      expect(() => buildPayload(edit!.form, createPayloadFormState(edit!.form))).not.toThrow();
    }
  });

  it('uses reference selectors for lifecycle IDs in visual mode', () => {
    expect(findField(resource('mappings').createForm, 'sourceStructureVersionId')).toMatchObject({
      kind: 'reference',
      reference: { assetType: 'structure-versions' },
    });
    expect(findField(resource('routes').createForm, 'connectorVersionId')).toMatchObject({
      kind: 'reference',
      reference: { assetType: 'connector-versions' },
    });
    expect(findField(resource('products').createForm, 'releaseSnapshotId')).toMatchObject({
      kind: 'reference',
      reference: { assetType: 'releases' },
    });
    expect(findField(resource('subscriptions').createForm, 'structureVersionIds')).toMatchObject({
      kind: 'reference',
      multiple: true,
      reference: { assetType: 'structure-versions' },
    });
    expect(findField(resource('policies').createForm, 'scopeType')).toMatchObject({
      clearKeysOnChange: ['scopeId'],
    });
    expect(findField(resource('policies').createForm, 'scopeId')).toMatchObject({
      kind: 'reference',
      referenceDependsOn: 'scopeType',
      referenceMap: {
        CLIENT: { assetType: 'application-clients' },
        PRODUCT: { assetType: 'products' },
        ROUTE: { assetType: 'routes', valuePath: 'assetKey' },
        SUBSCRIPTION: { assetType: 'subscriptions' },
      },
    });

    for (const lifecycleResource of Object.values(lifecycleResources)) {
      for (const action of lifecycleResource.actions) {
        expect(action.targetReference, `${action.label} should select a target asset`).toBeTruthy();
      }
    }
    expect(
      resource('routes').actions.find((item) => item.label === '发布路由版本')?.targetReference,
    ).toMatchObject({ assetType: 'route-versions' });
    expect(
      resource('subscriptions').actions.find((item) => item.label === '升级订阅')?.form
        ?.groups[0]?.fields[0],
    ).toMatchObject({ kind: 'reference', reference: { assetType: 'product-versions' } });
  });
});

function findField(form: PayloadFormDefinition, key: string): PayloadFormField {
  const field = findFieldInFields(
    form.groups.flatMap((group) => group.fields),
    key,
  );
  if (!field) {
    throw new Error(`Field ${key} not found`);
  }
  return field;
}

function findFieldInFields(fields: PayloadFormField[], key: string): PayloadFormField | undefined {
  for (const field of fields) {
    if (field.key === key) {
      return field;
    }
    const nested = findFieldInFields([...(field.fields ?? []), ...(field.itemFields ?? [])], key);
    if (nested) {
      return nested;
    }
  }
  return undefined;
}

function resource(key: string) {
  const value = lifecycleResources[key];
  if (!value) {
    throw new Error(`Resource ${key} not found`);
  }
  return value;
}
