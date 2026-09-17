package com.tec.dnsapi.dto;

import java.util.List;
import java.util.Map;

public record ExistsResponse(
        boolean exists,
        String name,
        String type,
        Integer ttl,
        List<Map<String, Object>> ips
) {
    public static ExistsResponse notFound(String name) {
        return new ExistsResponse(false, name, null, null, null);
    }
}