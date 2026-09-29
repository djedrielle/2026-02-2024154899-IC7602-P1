package com.tec.dnsapi.dto;

import java.time.Instant;

/**
 * Datos crudos de latencia de un registro round-trip: una medición por cada
 * combinación (checker, IP). El DNS Interceptor aplica la lógica de cercanía al
 * cliente y de menor latencia; el DNS API solo entrega los datos.
 */
public record LatencyResponse(
        String ip,
        Double latency_ms,
        Boolean is_healthy,
        String checker_location_id,
        Double checker_latitude,
        Double checker_longitude,
        String checker_country,
        String checker_city,
        Instant checked_at
) {}
