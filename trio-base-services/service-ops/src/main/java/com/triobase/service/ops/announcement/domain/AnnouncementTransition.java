package com.triobase.service.ops.announcement.domain;

import java.time.Instant;

public record AnnouncementTransition(
        AnnouncementState fromState,
        AnnouncementState toState,
        AnnouncementCommand command,
        Instant occurredAt,
        boolean reviewBypassed,
        String reason) {
}
