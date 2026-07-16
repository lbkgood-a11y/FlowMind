package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.exception.BizException;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class OutboundTargetPolicy {

    private static final int TARGET_REJECTED = 40030;

    public void validate(String baseUrl, JsonNode networkPolicy) {
        final URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            reject("OPENAPI_CONNECTOR_URL_INVALID");
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            reject("OPENAPI_CONNECTOR_SCHEME_NOT_ALLOWED");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            reject("OPENAPI_CONNECTOR_URL_AUTHORITY_INVALID");
        }
        Set<String> allowedHosts = textSet(networkPolicy == null ? null : networkPolicy.get("allowedHosts"));
        if (!allowedHosts.isEmpty() && !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            reject("OPENAPI_CONNECTOR_HOST_NOT_APPROVED");
        }
        boolean allowPrivate = networkPolicy != null && networkPolicy.path("allowPrivateNetwork").asBoolean(false);
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) {
                reject("OPENAPI_CONNECTOR_DNS_EMPTY");
            }
            for (InetAddress address : addresses) {
                if (!allowPrivate && isUnsafe(address)) {
                    reject("OPENAPI_CONNECTOR_PRIVATE_OR_METADATA_ADDRESS_REJECTED");
                }
            }
        } catch (UnknownHostException exception) {
            reject("OPENAPI_CONNECTOR_DNS_UNRESOLVED");
        }
    }

    private boolean isUnsafe(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            byte[] octets = address.getAddress();
            int first = Byte.toUnsignedInt(octets[0]);
            int second = Byte.toUnsignedInt(octets[1]);
            return first == 0 || first == 10 || first == 127
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || first >= 224;
        }
        return address.isLinkLocalAddress() || address.isSiteLocalAddress();
    }

    private Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        if (array != null && array.isArray()) {
            array.forEach(value -> values.add(value.asText().toLowerCase(Locale.ROOT)));
        }
        return values;
    }

    private void reject(String message) {
        throw new BizException(TARGET_REJECTED, message);
    }
}
