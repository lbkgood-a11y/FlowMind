import { requestClient } from '#/api/request';

export type AnnouncementState =
  | 'DRAFT'
  | 'EXPIRED'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'SCHEDULED'
  | 'SUPERSEDED'
  | 'WITHDRAWN';

export interface AnnouncementVersion {
  announcementId: string;
  audienceMode: 'DYNAMIC' | 'FROZEN';
  confirmationDeadline?: string;
  confirmationRequired: 0 | 1;
  content: string;
  effectiveUntil?: string;
  id: string;
  lifecycleState: AnnouncementState;
  pinFrom?: string;
  pinUntil?: string;
  priority: string;
  scheduledPublishAt?: string;
  title: string;
  versionNo: number;
}

export interface AnnouncementDraft {
  audience: Array<{
    includeDescendants?: boolean;
    resolverKey?: string;
    subjectIds?: string[];
    type: 'ALL' | 'DYNAMIC_PARTICIPANT' | 'ORGANIZATION' | 'ROLE' | 'USER';
  }>;
  confirmationDeadline?: string;
  confirmationRequired: boolean;
  confirmationStatement?: string;
  content: string;
  effectiveUntil?: string;
  pinFrom?: string;
  pinUntil?: string;
  priority: string;
  title: string;
}

export interface AnnouncementStatistics {
  accountableCount: number;
  calculatedAt: string;
  confirmedCount: number;
  overdueCount: number;
  readCount: number;
}

export function getAnnouncementVersions(params: Record<string, unknown>) {
  return requestClient.get<{
    page: number;
    records: AnnouncementVersion[];
    size: number;
    total: number;
  }>('/v2/announcements', { params });
}

export function createAnnouncementDraft(data: AnnouncementDraft) {
  return requestClient.post<AnnouncementVersion>('/v2/announcements', data);
}

export function createAnnouncementVersion(id: string, data: AnnouncementDraft) {
  return requestClient.post<AnnouncementVersion>(
    `/v2/announcements/${id}/versions`,
    data,
  );
}

export function submitAnnouncementReview(id: string) {
  return requestClient.post(`/v2/announcements/${id}/review`);
}

export function approveAnnouncement(id: string, scheduledPublishAt?: string) {
  return requestClient.post(`/v2/announcements/${id}/approve`, {
    scheduledPublishAt,
  });
}

export function rejectAnnouncement(id: string, reason: string) {
  return requestClient.post(`/v2/announcements/${id}/reject`, { reason });
}

export function withdrawAnnouncement(id: string, reason: string) {
  return requestClient.post(`/v2/announcements/${id}/withdraw`, { reason });
}

export function getAnnouncementStatistics(id: string) {
  return requestClient.get<AnnouncementStatistics>(
    `/v2/announcements/${id}/statistics`,
  );
}

export function remindAnnouncement(id: string, mode: 'UNCONFIRMED' | 'UNREAD') {
  return requestClient.post(`/v2/announcements/${id}/reminders`, undefined, {
    params: { mode, reminderKey: `manual-${id}-${Date.now()}` },
  });
}
