package com.tec.dnsapi.dto;

/**
 * Request recibido en POST /api/dns_resolver.
 * "data" contiene el paquete DNS crudo (RFC1035) codificado en BASE64.
 */
public record DnsResolverRequest(String data) {} // BASE64