import { requestClient } from '#/api/request';

export namespace OperationsApi {
  export interface PageResult<T> {
    page: number;
    records: T[];
    size: number;
    total: number;
  }

  export interface Announcement {
    content: string;
    id: string;
    priority?: string;
    publishAt?: string;
    status: string;
    targetOrgIds?: string;
    targetType?: string;
    targetUserIds?: string;
    title: string;
    unpublishAt?: string;
    updatedAt?: string;
  }

  export interface SaveAnnouncementParams {
    content: string;
    priority?: string;
    targetOrgIds?: string;
    targetType?: string;
    targetUserIds?: string;
    title: string;
  }

  export interface Message {
    content: string;
    createdAt?: string;
    id: string;
    messageType?: string;
    senderId?: string;
    senderName?: string;
    sourceId?: string;
    sourceType?: string;
    title: string;
  }

  export interface MessageRecipient {
    deletedAt?: string;
    id: string;
    messageId: string;
    readAt?: string;
    readStatus: 0 | 1;
    recipientUserId: string;
  }

  export interface MessageAdminResponse {
    message: Message;
    readCount: number;
    recipientCount: number;
    unreadCount: number;
  }

  export interface MessageInboxResponse {
    message: Message;
    recipient: MessageRecipient;
  }

  export interface SendMessageParams {
    content: string;
    messageType?: string;
    recipientUserIds: string[];
    sourceId?: string;
    sourceType?: string;
    title: string;
  }

  export interface OpsFile {
    checksum?: string;
    contentType?: string;
    downloadCount?: number;
    extension?: string;
    fileSize: number;
    id: string;
    lastDownloadAt?: string;
    originalName: string;
    ownerUserId?: string;
    status: 0 | 1;
    storageName: string;
    updatedAt?: string;
  }

  export interface FileReference {
    businessId: string;
    businessType: string;
    fileId: string;
    id: string;
    refType?: string;
  }

  export interface BindFileReferenceParams {
    businessId: string;
    businessType: string;
    fileId: string;
    refType?: string;
  }

  export interface ImportExportTask {
    businessType: string;
    createdAt?: string;
    createdBy?: string;
    failureCount?: number;
    failureFileId?: string;
    failureReason?: string;
    finishedAt?: string;
    id: string;
    progress: number;
    requestParams?: string;
    resultFileId?: string;
    startedAt?: string;
    status: string;
    successCount?: number;
    taskName: string;
    taskType: string;
  }

  export interface CreateTaskParams {
    businessType: string;
    requestParams?: string;
    taskName: string;
  }

  export interface UpdateTaskParams {
    failureCount?: number;
    failureFileId?: string;
    failureReason?: string;
    progress?: number;
    resultFileId?: string;
    status?: string;
    successCount?: number;
  }

  export interface JobDefinition {
    cronExpression: string;
    description?: string;
    enabled: 0 | 1;
    handlerName: string;
    id: string;
    jobCode: string;
    jobName: string;
    jobParams?: string;
    lastRunAt?: string;
    nextRunAt?: string;
  }

  export interface SaveJobParams {
    cronExpression: string;
    description?: string;
    enabled?: 0 | 1;
    handlerName: string;
    jobCode: string;
    jobName: string;
    jobParams?: string;
  }

  export interface JobExecutionLog {
    durationMs?: number;
    endedAt?: string;
    errorMessage?: string;
    id: string;
    jobCode: string;
    jobId: string;
    resultSummary?: string;
    runInstance?: string;
    startedAt?: string;
    status: string;
    triggerType: string;
    triggeredBy?: string;
  }
}

async function getAnnouncementPage(params?: Record<string, any>) {
  return requestClient.get<OperationsApi.PageResult<OperationsApi.Announcement>>(
    '/announcements',
    { params },
  );
}

async function createAnnouncement(data: OperationsApi.SaveAnnouncementParams) {
  return requestClient.post<OperationsApi.Announcement>('/announcements', data);
}

async function updateAnnouncement(id: string, data: OperationsApi.SaveAnnouncementParams) {
  return requestClient.put<OperationsApi.Announcement>(`/announcements/${id}`, data);
}

async function deleteAnnouncement(id: string) {
  return requestClient.delete(`/announcements/${id}`);
}

async function publishAnnouncement(id: string) {
  return requestClient.post<OperationsApi.Announcement>(`/announcements/${id}/publish`);
}

async function unpublishAnnouncement(id: string) {
  return requestClient.post<OperationsApi.Announcement>(`/announcements/${id}/unpublish`);
}

async function getMessagePage(params?: Record<string, any>) {
  return requestClient.get<OperationsApi.PageResult<OperationsApi.MessageAdminResponse>>(
    '/messages',
    { params },
  );
}

async function sendMessage(data: OperationsApi.SendMessageParams) {
  return requestClient.post<OperationsApi.Message>('/messages', data);
}

async function deleteMessage(id: string) {
  return requestClient.delete(`/messages/${id}`);
}

async function getInboxMessages(params?: Record<string, any>) {
  return requestClient.get<OperationsApi.PageResult<OperationsApi.MessageInboxResponse>>(
    '/messages/inbox',
    { params },
  );
}

async function markInboxMessageRead(recipientId: string) {
  return requestClient.post(`/messages/inbox/${recipientId}/read`);
}

async function deleteInboxMessage(recipientId: string) {
  return requestClient.delete(`/messages/inbox/${recipientId}`);
}

async function getFilePage(params?: Record<string, any>) {
  return requestClient.get<OperationsApi.PageResult<OperationsApi.OpsFile>>('/files', {
    params,
  });
}

async function uploadFile(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return requestClient.post<OperationsApi.OpsFile>('/files', formData);
}

function getFileDownloadUrl(id: string) {
  return `/api/files/${id}`;
}

async function updateFileStatus(id: string, status: 0 | 1) {
  return requestClient.put<OperationsApi.OpsFile>(`/files/${id}/status`, undefined, {
    params: { status },
  });
}

async function deleteFile(id: string) {
  return requestClient.delete(`/files/${id}`);
}

async function getFileReferences(params?: Record<string, any>) {
  return requestClient.get<OperationsApi.FileReference[]>('/file-references', {
    params,
  });
}

async function bindFileReference(data: OperationsApi.BindFileReferenceParams) {
  return requestClient.post<OperationsApi.FileReference>('/file-references', data);
}

async function getImportExportTasks(params?: Record<string, any>) {
  return requestClient.get<OperationsApi.PageResult<OperationsApi.ImportExportTask>>(
    '/import-export-tasks',
    { params },
  );
}

async function createImportTask(data: OperationsApi.CreateTaskParams) {
  return requestClient.post<OperationsApi.ImportExportTask>(
    '/import-export-tasks/import',
    data,
  );
}

async function createExportTask(data: OperationsApi.CreateTaskParams) {
  return requestClient.post<OperationsApi.ImportExportTask>(
    '/import-export-tasks/export',
    data,
  );
}

async function cancelImportExportTask(id: string) {
  return requestClient.post<OperationsApi.ImportExportTask>(
    `/import-export-tasks/${id}/cancel`,
  );
}

async function updateImportExportTask(id: string, data: OperationsApi.UpdateTaskParams) {
  return requestClient.put<OperationsApi.ImportExportTask>(
    `/import-export-tasks/${id}`,
    data,
  );
}

async function getImportExportTaskResult(id: string) {
  return requestClient.get<OperationsApi.ImportExportTask>(
    `/import-export-tasks/${id}/result`,
  );
}

async function getJobs(params?: Record<string, any>) {
  return requestClient.get<OperationsApi.PageResult<OperationsApi.JobDefinition>>('/jobs', {
    params,
  });
}

async function createJob(data: OperationsApi.SaveJobParams) {
  return requestClient.post<OperationsApi.JobDefinition>('/jobs', data);
}

async function updateJob(id: string, data: OperationsApi.SaveJobParams) {
  return requestClient.put<OperationsApi.JobDefinition>(`/jobs/${id}`, data);
}

async function updateJobEnabled(id: string, enabled: 0 | 1) {
  return requestClient.put<OperationsApi.JobDefinition>(`/jobs/${id}/enabled`, undefined, {
    params: { enabled },
  });
}

async function deleteJob(id: string) {
  return requestClient.delete(`/jobs/${id}`);
}

async function triggerJob(id: string) {
  return requestClient.post<OperationsApi.JobExecutionLog>(`/jobs/${id}/trigger`);
}

async function getJobLogs(id: string, params?: Record<string, any>) {
  return requestClient.get<OperationsApi.PageResult<OperationsApi.JobExecutionLog>>(
    `/jobs/${id}/logs`,
    { params },
  );
}

export {
  bindFileReference,
  cancelImportExportTask,
  createAnnouncement,
  createExportTask,
  createImportTask,
  createJob,
  deleteAnnouncement,
  deleteFile,
  deleteInboxMessage,
  deleteJob,
  deleteMessage,
  getAnnouncementPage,
  getFileDownloadUrl,
  getFilePage,
  getFileReferences,
  getImportExportTaskResult,
  getImportExportTasks,
  getInboxMessages,
  getJobLogs,
  getJobs,
  getMessagePage,
  markInboxMessageRead,
  publishAnnouncement,
  sendMessage,
  triggerJob,
  unpublishAnnouncement,
  updateAnnouncement,
  updateFileStatus,
  updateImportExportTask,
  updateJob,
  updateJobEnabled,
  uploadFile,
};
