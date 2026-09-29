package com.tec.dnsapi.dto;

import java.util.UUID;

public record HealthResultResponse(
        Long id,
        UUID target_id,
        String record_name,
        String ip_address,
        Boolean is_healthy,
        Double latency_ms,
        String checker_location_id,
        Double checker_latitude,
        Double checker_longitude,
        String checker_country,
        String checker_city,
        String checked_at) {
}
