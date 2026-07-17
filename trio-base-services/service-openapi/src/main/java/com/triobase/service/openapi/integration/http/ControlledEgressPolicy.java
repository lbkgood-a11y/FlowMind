package com.triobase.service.openapi.integration.http;

import com.triobase.service.openapi.domain.entity.ConnectorVersion;

import java.net.URI;

public interface ControlledEgressPolicy {
    void authorize(ConnectorVersion connector, URI target);
}
