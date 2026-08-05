package com.triobase.service.ops.announcement.dto;

import java.time.Instant;

public record AnnouncementTransitionCommandRequest(Instant scheduledPublishAt, String reason) {
}
