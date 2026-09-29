package com.tec.dnsapi.validation;

import com.tec.dnsapi.dto.RecordRequest;
import com.tec.dnsapi.exception.InvalidRequestException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Valida el body de POST/PUT /api/records según el contrato de docs/schema.md. */
public final class RecordValidator {

    public static final Set<String> TYPES = Set.of("single", "multi", "weight", "round-trip", "geo");

    private static final int MAX_NAME_LENGTH = 253;
    private static final int MAX_IPS = 100;
    private static final Pattern NAME = Pattern.compile(
            "^(\\*\\.)?([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?\\.)*[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?\\.?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");

    private RecordValidator() {}

    /** @param requireName true en POST; en PUT el nombre viene en la ruta y el del body es opcional. */
    public static void validate(RecordRequest req, boolean requireName) {
        if (req == null) {
            throw new InvalidRequestException("El cuerpo de la petición es obligatorio");
        }
        validateName(req.name(), requireName);

        if (req.type() == null || !TYPES.contains(req.type())) {
            throw new InvalidRequestException("'type' debe ser uno de: single, multi, weight, round-trip, geo");
        }
        if (req.ttl() == null || req.ttl() < 0) {
            throw new InvalidRequestException("'ttl' es obligatorio y no puede ser negativo");
        }
        validateIps(req.type(), req.ips());
    }

    private static void validateName(String name, boolean required) {
        if (name == null || name.isBlank()) {
            if (required) {
                throw new InvalidRequestException("'name' es obligatorio");
            }
            return;
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH || !NAME.matcher(trimmed).matches()) {
            throw new InvalidRequestException("'name' no es un nombre de dominio válido: " + name);
        }
    }

    private static void validateIps(String type, List<Map<String, Object>> ips) {
        if (ips == null || ips.isEmpty()) {
            throw new InvalidRequestException("'ips' debe contener al menos una IP");
        }
        if (ips.size() > MAX_IPS) {
            throw new InvalidRequestException("'ips' admite como máximo " + MAX_IPS + " elementos");
        }
        for (int i = 0; i < ips.size(); i++) {
            Map<String, Object> entry = ips.get(i);
            String where = "ips[" + i + "]";
            if (entry == null || !(entry.get("ip") instanceof String ip) || !IpAddresses.isValid(ip)) {
                throw new InvalidRequestException(where + ".ip no es una IP válida");
            }
            Object healthy = entry.get("healthy");
            if (healthy != null && !(healthy instanceof Boolean)) {
                throw new InvalidRequestException(where + ".healthy debe ser booleano");
            }
            switch (type) {
                case "weight" -> requireNumber(entry, "weight", where, 0, Double.MAX_VALUE);
                case "round-trip" -> {
                    requireNumber(entry, "latitude", where, -90, 90);
                    requireNumber(entry, "longitude", where, -180, 180);
                }
                case "geo" -> {
                    if (!(entry.get("country_code") instanceof String cc) || !COUNTRY.matcher(cc).matches()) {
                        throw new InvalidRequestException(where + ".country_code debe ser un código de 2 letras");
                    }
                }
                default -> { }
            }
        }
    }

    private static void requireNumber(Map<String, Object> entry, String field, String where, double min, double max) {
        if (!(entry.get(field) instanceof Number number)
                || Double.isNaN(number.doubleValue())
                || number.doubleValue() < min || number.doubleValue() > max) {
            throw new InvalidRequestException(where + "." + field + " debe ser un número entre " + min + " y " + max);
        }
    }
}
