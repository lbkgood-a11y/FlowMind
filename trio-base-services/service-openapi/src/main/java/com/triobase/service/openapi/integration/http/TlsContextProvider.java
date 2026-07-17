package com.triobase.service.openapi.integration.http;

import javax.net.ssl.SSLContext;

public interface TlsContextProvider {
    SSLContext resolve(String tlsProfileReference);
}
