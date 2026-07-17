package com.triobase.service.openapi.integration.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.integration.credential.OutboundAuthentication;
import com.triobase.service.openapi.integration.credential.OutboundAuthenticationResolver;
import com.triobase.service.openapi.service.OutboundTargetPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class JdkOutboundIntegrationClient implements OutboundIntegrationClient {

    private static final Set<String> RESERVED_HEADERS = Set.of(
            "host", "content-length", "authorization", "proxy-authorization", "cookie");
    private final OutboundTargetPolicy targetPolicy;
    private final OutboundAuthenticationResolver authenticationResolver;
    private final MeterRegistry meterRegistry;
    private final List<ControlledEgressPolicy> egressPolicies;
    private final TlsContextProvider tlsContextProvider;
    private final ObjectMapper objectMapper;
    private final HttpClient defaultClient;

    @Autowired
    public JdkOutboundIntegrationClient(
            OutboundTargetPolicy targetPolicy,
            OutboundAuthenticationResolver authenticationResolver,
            MeterRegistry meterRegistry,
            ObjectProvider<ControlledEgressPolicy> egressPolicies,
            ObjectProvider<TlsContextProvider> tlsContextProvider,
            ObjectMapper objectMapper) {
        this(targetPolicy, authenticationResolver, meterRegistry,
                egressPolicies.orderedStream().toList(), tlsContextProvider.getIfAvailable(), objectMapper);
    }

    JdkOutboundIntegrationClient(
            OutboundTargetPolicy targetPolicy,
            OutboundAuthenticationResolver authenticationResolver,
            MeterRegistry meterRegistry,
            List<ControlledEgressPolicy> egressPolicies,
            TlsContextProvider tlsContextProvider,
            ObjectMapper objectMapper) {
        this.targetPolicy = targetPolicy;
        this.authenticationResolver = authenticationResolver;
        this.meterRegistry = meterRegistry;
        this.egressPolicies = List.copyOf(egressPolicies);
        this.tlsContextProvider = tlsContextProvider;
        this.objectMapper = objectMapper;
        this.defaultClient = newClient(null);
    }

    @Override
    public OutboundResponse execute(OutboundRequest request) {
        ConnectorVersion connector = requireConnector(request);
        targetPolicy.validate(connector.getBaseUrl(), connector.getNetworkPolicy());
        byte[] requestBody = serialize(request.body());
        OutboundAuthentication authentication = authenticationResolver.resolve(
                connector.getAuthenticationType(), request.credential(), requestBody);
        URI target = targetUri(connector, authentication.queryParameters());
        egressPolicies.forEach(policy -> policy.authorize(connector, target));
        HttpClient client = authentication.tlsProfileReference() == null
                ? defaultClient : mtlsClient(authentication.tlsProfileReference());
        HttpRequest httpRequest = buildRequest(request, connector, authentication, target, requestBody);
        long started = System.nanoTime();
        String outcome = "io_error";
        try {
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            outcome = Integer.toString(response.statusCode());
            byte[] body = readLimited(response.body(), connector.getResponseSizeLimit());
            long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.info("Outbound connector call completed connectorVersionId={} host={} status={} durationMs={}",
                    connector.getId(), target.getHost(), response.statusCode(), duration);
            return new OutboundResponse(response.statusCode(), response.headers().map(), body, duration);
        } catch (BizException exception) {
            outcome = "rejected";
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(50232, "OPENAPI_OUTBOUND_CALL_INTERRUPTED");
        } catch (Exception exception) {
            throw new BizException(50232, "OPENAPI_OUTBOUND_CALL_FAILED");
        } finally {
            Timer.builder("triobase.openapi.outbound.duration")
                    .tag("operationClass", connector.getOperationClass().name())
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private ConnectorVersion requireConnector(OutboundRequest request) {
        if (request == null || request.connector() == null
                || request.connector().getBaseUrl() == null || request.connector().getOperationPath() == null) {
            throw new BizException(40033, "OPENAPI_OUTBOUND_CONNECTOR_REQUIRED");
        }
        return request.connector();
    }

    private HttpRequest buildRequest(
            OutboundRequest request, ConnectorVersion connector, OutboundAuthentication authentication,
            URI target, byte[] requestBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(connector.getTimeoutMillis()));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (request.headers() != null) {
            request.headers().forEach((name, values) -> {
                if (!RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    headers.put(name, List.copyOf(values));
                }
            });
        }
        authentication.headers().forEach((name, value) -> headers.put(name, List.of(value)));
        String traceId = TraceUtil.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            headers.put(TraceUtil.TRACE_ID_KEY, List.of(traceId));
        }
        if (requestBody.length > 0 && headers.keySet().stream().noneMatch("content-type"::equalsIgnoreCase)) {
            headers.put("Content-Type", List.of("application/json"));
        }
        headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        HttpRequest.BodyPublisher bodyPublisher = requestBody.length == 0
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(requestBody);
        return builder.method(connector.getHttpMethod(), bodyPublisher).build();
    }

    private URI targetUri(ConnectorVersion connector, Map<String, String> queryParameters) {
        String base = connector.getBaseUrl().endsWith("/")
                ? connector.getBaseUrl().substring(0, connector.getBaseUrl().length() - 1)
                : connector.getBaseUrl();
        String path = connector.getOperationPath().startsWith("/")
                ? connector.getOperationPath() : "/" + connector.getOperationPath();
        StringBuilder target = new StringBuilder(base).append(path);
        if (!queryParameters.isEmpty()) {
            target.append(target.indexOf("?") >= 0 ? '&' : '?');
            List<String> pairs = new ArrayList<>();
            queryParameters.forEach((name, value) -> pairs.add(encode(name) + "=" + encode(value)));
            target.append(String.join("&", pairs));
        }
        URI uri = URI.create(target.toString());
        targetPolicy.validate(uri.getScheme() + "://" + uri.getAuthority(), connector.getNetworkPolicy());
        return uri;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private byte[] serialize(com.fasterxml.jackson.databind.JsonNode body) {
        if (body == null || body.isNull()) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception exception) {
            throw new BizException(40033, "OPENAPI_OUTBOUND_BODY_INVALID");
        }
    }

    private byte[] readLimited(InputStream input, long limit) throws Exception {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new BizException(50233, "OPENAPI_OUTBOUND_RESPONSE_TOO_LARGE");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private HttpClient mtlsClient(String profileReference) {
        if (tlsContextProvider == null) {
            throw new BizException(50332, "OPENAPI_MTLS_PROVIDER_UNAVAILABLE");
        }
        return newClient(tlsContextProvider.resolve(profileReference));
    }

    private HttpClient newClient(SSLContext sslContext) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10));
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }
}
