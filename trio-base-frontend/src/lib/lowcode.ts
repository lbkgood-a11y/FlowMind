import { api } from "@/lib/api";

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface FormFieldSchema {
  fieldKey: string;
  label: string;
  fieldType: string;
  required?: boolean;
  defaultValue?: string;
  placeholder?: string;
  optionsJson?: string;
  sortOrder?: number;
}

export interface FormDefinition {
  id: string;
  formKey: string;
  name: string;
  description?: string;
  version: number;
  status: string;
  schemaJson?: string;
  uiSchemaJson?: string;
  createdBy?: string;
  createdAt?: string;
  fields?: FormFieldSchema[];
}

export interface CreateFormDefinitionRequest {
  formKey: string;
  name: string;
  description?: string;
  schemaJson?: string;
  uiSchemaJson?: string;
  fields: FormFieldSchema[];
}

export interface FormInstance {
  id: string;
  formDefinitionId: string;
  formKey: string;
  status: string;
  dataJson: string;
  submittedBy?: string;
  submittedAt?: string;
}

export interface SubmitFormInstanceRequest {
  submittedBy?: string;
  data: Record<string, unknown>;
}

export const lowcodeApi = {
  listForms: async (page = 1, size = 20) => {
    const response = await api.get<ApiResponse<PageResult<FormDefinition>>>("/api/v1/forms", {
      params: { page: String(page), size: String(size) },
    });
    return response.data;
  },

  getForm: async (id: string) => {
    const response = await api.get<ApiResponse<FormDefinition>>(`/api/v1/forms/${id}`);
    return response.data;
  },

  createForm: async (payload: CreateFormDefinitionRequest) => {
    const response = await api.post<ApiResponse<FormDefinition>>("/api/v1/forms", payload);
    return response.data;
  },

  publishForm: async (id: string) => {
    const response = await api.put<ApiResponse<FormDefinition>>(`/api/v1/forms/${id}/publish`);
    return response.data;
  },

  submitForm: async (formKey: string, payload: SubmitFormInstanceRequest) => {
    const response = await api.post<ApiResponse<FormInstance>>(`/api/v1/forms/${formKey}/submit`, payload);
    return response.data;
  },
};
