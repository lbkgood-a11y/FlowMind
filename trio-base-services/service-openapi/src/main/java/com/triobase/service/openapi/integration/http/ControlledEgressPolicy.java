package com.triobase.service.openapi.integration.http;

import com.triobase.common.openapi.entity.ConnectorVersion;

import java.net.URI;

public interface ControlledEgressPolicy {
    void authorize(ConnectorVersion connector, URI target);
}
