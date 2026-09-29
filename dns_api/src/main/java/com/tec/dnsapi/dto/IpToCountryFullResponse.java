package com.tec.dnsapi.dto;

/**
 * Response completo de un registro ip_to_country.
 * Usado en GET /api/ip_country (lista) y POST/PUT /api/ip_country.
 *
 * Para el endpoint GET /api/ip_country?ip=X (usado por el Interceptor)
 * se sigue usando IpCountryResponse (solo country_code).
 */
public record IpToCountryFullResponse(
                Long id,
                String start_ip,
                String end_ip,
                String country_code,
                String country_name,
                String city,
                Double latitude,
                Double longitude) {
}
