package com.tec.dnsapi.dto;

import java.util.List;
import java.util.Map;

public record RecordResponse(
        String name,
        String type,
        Integer ttl,
        List<Map<String, Object>> ips
) {}