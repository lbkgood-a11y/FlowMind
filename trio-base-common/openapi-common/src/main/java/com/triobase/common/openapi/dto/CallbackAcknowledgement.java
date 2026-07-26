package com.triobase.common.openapi.dto;

public record CallbackAcknowledgement(
        String inboxId,
        int status,
        String contentType,
        String body,
        boolean duplicate,
        boolean quarantined) {
}
