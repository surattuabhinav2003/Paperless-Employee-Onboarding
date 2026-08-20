package com.cloudfuze.onboarding.util;

import jakarta.servlet.http.HttpServletRequest;

/** Helpers for pulling audit-relevant context off the current request. */
public final class RequestContext {

    private static final String[] FORWARD_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP"
    };

    private RequestContext() {
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : FORWARD_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // X-Forwarded-For may carry a proxy chain; the client is first.
                int comma = value.indexOf(',');
                return (comma > 0 ? value.substring(0, comma) : value).trim();
            }
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
