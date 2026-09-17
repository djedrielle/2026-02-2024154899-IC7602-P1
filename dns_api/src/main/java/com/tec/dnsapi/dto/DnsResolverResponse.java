package com.tec.dnsapi.dto;

/**
 * Response de POST /api/dns_resolver.
 * "data" contiene el paquete DNS de respuesta codificado en BASE64.
 */
public record DnsResolverResponse(String data) {} // BASE64