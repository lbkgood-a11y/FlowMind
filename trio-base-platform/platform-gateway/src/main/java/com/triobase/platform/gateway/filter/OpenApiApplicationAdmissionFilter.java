package com.triobase.platform.gateway.filter;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.integration.IntegrationAdmissionDecision;
import com.triobase.common.dto.integration.IntegrationAdmissionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
@Component
public class OpenApiApplicationAdmissionFilter implements GlobalFilter, Ordered {
 private static final String RUNTIME="/api/v1/openapi/runtime/",CALLBACK="/api/v1/openapi/callbacks/";
 private final WebClient client;private final boolean enabled;
 public OpenApiApplicationAdmissionFilter(WebClient.Builder builder,@Value("${triobase.openapi.service-url:http://localhost:8088}")String serviceUrl,@Value("${triobase.openapi.runtime.enabled:false}")boolean enabled){this.client=builder.baseUrl(serviceUrl).build();this.enabled=enabled;}
 @Override public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){String path=exchange.getRequest().getURI().getPath();if(!enabled||(!path.startsWith(RUNTIME)&&!path.startsWith(CALLBACK)))return chain.filter(exchange);HttpMethod method=exchange.getRequest().getMethod();long length=exchange.getRequest().getHeaders().getContentLength();if(length<0&&(method==HttpMethod.POST||method==HttpMethod.PUT||method==HttpMethod.PATCH))return reject(exchange,411,"CONTENT_LENGTH_REQUIRED",0);IntegrationAdmissionRequest request=new IntegrationAdmissionRequest(header(exchange,"X-Tenant-Id"),header(exchange,"X-Client-Key"),header(exchange,"X-Environment"),routeKey(path),method.name(),credential(exchange),sourceIp(exchange),Math.max(0,length),exchange.getRequest().getSslInfo()!=null,scopes(exchange),header(exchange,"X-Signature-Timestamp"),header(exchange,"X-Signature-Nonce"),header(exchange,"X-Signature"),header(exchange,"X-Content-SHA256"),certificateFingerprint(exchange),path.startsWith(CALLBACK));return client.post().uri("/api/v1/openapi/internal/admission").bodyValue(request).retrieve().bodyToMono(new ParameterizedTypeReference<R<IntegrationAdmissionDecision>>(){}).flatMap(result->{IntegrationAdmissionDecision decision=result==null?null:result.getData();if(decision==null||!decision.allowed())return reject(exchange,decision==null?503:decision.httpStatus(),decision==null?"ADMISSION_UNAVAILABLE":decision.reason(),decision==null?0:decision.retryAfterSeconds());ServerWebExchange admitted=exchange.mutate().request(builder->builder.headers(headers->{headers.remove("X-Client-Credential");headers.remove(HttpHeaders.AUTHORIZATION);headers.set("X-Tenant-Id",decision.tenantId());headers.set("X-User-Id","APP:"+decision.applicationClientId());headers.set("X-Username",decision.applicationClientId());headers.set("X-User-Roles","EXTERNAL_APPLICATION");headers.set("X-Application-Client-Id",decision.applicationClientId());headers.set("X-Subscription-Id",decision.subscriptionId());headers.set("X-Policy-Version",Long.toString(decision.policyVersion()));headers.set("X-Max-Concurrency",Long.toString(decision.maxConcurrency()));headers.set("X-Max-Active-Workflows",Long.toString(decision.maxActiveWorkflows()));headers.set("X-Gateway-Authenticated","true");})).build();return chain.filter(admitted);}).onErrorResume(error->reject(exchange,503,"ADMISSION_UNAVAILABLE",1));}
 private Mono<Void> reject(ServerWebExchange exchange,int status,String reason,long retry){exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));exchange.getResponse().getHeaders().set("X-OpenAPI-Denial-Reason",reason);if(status==429)exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER,Long.toString(Math.max(1,retry)));return exchange.getResponse().setComplete();}
 private String header(ServerWebExchange e,String name){return e.getRequest().getHeaders().getFirst(name);}private String credential(ServerWebExchange e){String value=header(e,"X-Client-Credential");return StringUtils.hasText(value)?value:header(e,HttpHeaders.AUTHORIZATION);}private Set<String> scopes(ServerWebExchange e){String value=header(e,"X-Scopes");return !StringUtils.hasText(value)?Set.of():Arrays.stream(value.split("[, ]+")).filter(StringUtils::hasText).collect(Collectors.toUnmodifiableSet());}
 private String routeKey(String path){String prefix=path.startsWith(RUNTIME)?RUNTIME:CALLBACK;String rest=path.substring(prefix.length());int slash=rest.indexOf('/');return URLDecoder.decode(slash<0?rest:rest.substring(0,slash),StandardCharsets.UTF_8);}private String sourceIp(ServerWebExchange e){InetSocketAddress address=e.getRequest().getRemoteAddress();return address==null?"":address.getAddress().getHostAddress();}
 private String certificateFingerprint(ServerWebExchange e){try{if(e.getRequest().getSslInfo()==null||e.getRequest().getSslInfo().getPeerCertificates()==null||e.getRequest().getSslInfo().getPeerCertificates().length==0)return null;Certificate certificate=e.getRequest().getSslInfo().getPeerCertificates()[0];return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));}catch(Exception ex){return null;}}
 @Override public int getOrder(){return Ordered.HIGHEST_PRECEDENCE+5;}
}
