package com.tec.dnsapi.validation;

import com.tec.dnsapi.dto.TargetRequest;
import com.tec.dnsapi.exception.InvalidRequestException;

import java.util.List;

/** Valida el body de POST/PUT /api/targets. */
public final class TargetValidator {

    private static final int MAX_TIMEOUT_MS = 600_000;
    private static final int MAX_RETRIES = 100;

    private TargetValidator() {}

    /** Devuelve el check_type normalizado en mayúsculas (TCP o HTTP). */
    public static String validate(TargetRequest req) {
        if (req == null) {
            throw new InvalidRequestException("El cuerpo de la petición es obligatorio");
        }
        if (req.record_name() == null || req.record_name().isBlank()) {
            throw new InvalidRequestException("'record_name' es obligatorio");
        }
        if (!IpAddresses.isValid(req.ip_address())) {
            throw new InvalidRequestException("'ip_address' no es una IP válida");
        }
        if (req.port() == null || req.port() < 1 || req.port() > 65535) {
            throw new InvalidRequestException("'port' debe estar entre 1 y 65535");
        }
        String type = req.check_type() == null ? "" : req.check_type().trim().toUpperCase();
        if (!type.equals("TCP") && !type.equals("HTTP")) {
            throw new InvalidRequestException("'check_type' debe ser TCP o HTTP");
        }
        if (req.timeout_ms() == null || req.timeout_ms() < 1 || req.timeout_ms() > MAX_TIMEOUT_MS) {
            throw new InvalidRequestException("'timeout_ms' debe estar entre 1 y " + MAX_TIMEOUT_MS);
        }
        if (req.retries() == null || req.retries() < 0 || req.retries() > MAX_RETRIES) {
            throw new InvalidRequestException("'retries' debe estar entre 0 y " + MAX_RETRIES);
        }
        if (type.equals("HTTP")) {
            if (req.http_path() == null || !req.http_path().startsWith("/")) {
                throw new InvalidRequestException("'http_path' es obligatorio para HTTP y debe comenzar con '/'");
            }
            validateStatusCodes(req.expected_status_codes());
        }
        return type;
    }

    private static void validateStatusCodes(List<Integer> codes) {
        if (codes == null) {
            return;
        }
        for (Integer code : codes) {
            if (code == null || code < 100 || code > 599) {
                throw new InvalidRequestException("'expected_status_codes' solo admite códigos HTTP entre 100 y 599");
            }
        }
    }
}
