package com.tec.dnsapi.dto;

/**
 * Body recibido en POST /api/ip_country y PUT /api/ip_country/{id}.
 */
public record IpToCountryRequest(
                String start_ip,
                String end_ip,
                String country_code,
                String country_name,
                String city,
                Double latitude,
                Double longitude) {
}
