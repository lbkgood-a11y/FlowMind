import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace DataApi {
  export interface DatasetField {
    fieldKey: string;
    label: string;
    fieldType: string;
    searchable?: boolean;
    sortable?: boolean;
    sortOrder?: number;
  }

  export interface Dataset {
    id: string;
    datasetKey: string;
    name: string;
    datasetType: string;
    ownerId?: string;
    ownerName?: string;
    status: string;
    backingTable?: string;
    description?: string;
    fields?: DatasetField[];
    createdAt?: string;
    updatedAt?: string;
  }

  export interface DocumentIngestResponse {
    documentId: string;
    collectionKey: string;
    sourceKey?: string;
    title: string;
    chunkCount: number;
  }

  export interface StructuredQueryResponse {
    status: string;
    datasetKey?: string;
    total: number;
    page: number;
    size: number;
    elapsedMs: number;
    fields: DatasetField[];
    rows: Recordable<any>[];
  }

  export interface SemanticChunk {
    documentId: string;
    title: string;
    collectionKey: string;
    chunkIndex: number;
    content: string;
    score: number;
  }

  export interface SemanticQueryResponse {
    status: string;
    collectionKey?: string;
    topK: number;
    elapsedMs: number;
    chunks: SemanticChunk[];
  }

  export interface HybridQueryResponse {
    mode: string;
    structured: StructuredQueryResponse;
    semantic: SemanticQueryResponse;
    elapsedMs: number;
  }
}

async function getDatasetList(params: Recordable<any>) {
  const page = await requestClient.get<{
    records: DataApi.Dataset[];
    total: number;
  }>('/data/datasets', { params });
  return { items: page.records, total: page.total };
}

async function createDataset(data: {
  backingTable?: string;
  datasetKey: string;
  datasetType?: string;
  description?: string;
  fields?: DataApi.DatasetField[];
  name: string;
}) {
  return requestClient.post<DataApi.Dataset>('/data/datasets', data);
}

async function ingestDocument(data: {
  collectionKey: string;
  content: string;
  datasetId?: string;
  sourceKey?: string;
  title: string;
}) {
  return requestClient.post<DataApi.DocumentIngestResponse>('/data/documents', data);
}

async function runHybridQuery(data: Recordable<any>) {
  return requestClient.post<DataApi.HybridQueryResponse>('/data/query/hybrid', data);
}

export { createDataset, getDatasetList, ingestDocument, runHybridQuery };
