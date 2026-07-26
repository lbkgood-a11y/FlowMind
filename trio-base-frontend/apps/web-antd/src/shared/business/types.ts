export interface BusinessTimelineEntry {
  actionId?: string;
  actionStatus?: string;
  actionType?: string;
  actorId?: string;
  actorName?: string;
  correlationId?: string;
  displayName?: string;
  eventId: string;
  eventSource?: string;
  eventType?: string;
  occurredAt?: string;
  ownerExecutionRef?: string;
  ownerService?: string;
  redacted?: boolean;
  sequenceNo?: number;
  summary?: Record<string, unknown>;
  targetId?: string;
  targetType?: string;
  tenantId?: string;
  traceId?: string;
}
