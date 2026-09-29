package com.tec.dnsapi.validation;

import com.tec.dnsapi.dto.IpToCountryRequest;
import com.tec.dnsapi.exception.InvalidRequestException;

import java.util.regex.Pattern;

/** Valida el body de POST/PUT /api/ip_country. */
public final class IpCountryValidator {

    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");
    private static final int MAX_TEXT = 255;

    private IpCountryValidator() {}

    public static void validate(IpToCountryRequest req) {
        if (req == null) {
            throw new InvalidRequestException("El cuerpo de la petición es obligatorio");
        }
        byte[] start = IpAddresses.parse(req.start_ip());
        byte[] end = IpAddresses.parse(req.end_ip());
        if (start == null) {
            throw new InvalidRequestException("'start_ip' no es una IP válida");
        }
        if (end == null) {
            throw new InvalidRequestException("'end_ip' no es una IP válida");
        }
        if (start.length != end.length) {
            throw new InvalidRequestException("'start_ip' y 'end_ip' deben ser de la misma familia (IPv4 o IPv6)");
        }
        if (IpAddresses.compare(start, end) > 0) {
            throw new InvalidRequestException("'start_ip' no puede ser mayor que 'end_ip'");
        }
        if (req.country_code() == null || !COUNTRY.matcher(req.country_code()).matches()) {
            throw new InvalidRequestException("'country_code' es obligatorio y debe tener 2 letras");
        }
        requireShort(req.country_name(), "country_name");
        requireShort(req.city(), "city");
        requireRange(req.latitude(), -90, 90, "latitude");
        requireRange(req.longitude(), -180, 180, "longitude");
    }

    private static void requireShort(String value, String field) {
        if (value != null && value.length() > MAX_TEXT) {
            throw new InvalidRequestException("'" + field + "' admite como máximo " + MAX_TEXT + " caracteres");
        }
    }

    private static void requireRange(Double value, double min, double max, String field) {
        if (value != null && (value.isNaN() || value < min || value > max)) {
            throw new InvalidRequestException("'" + field + "' debe estar entre " + min + " y " + max);
        }
    }
}
