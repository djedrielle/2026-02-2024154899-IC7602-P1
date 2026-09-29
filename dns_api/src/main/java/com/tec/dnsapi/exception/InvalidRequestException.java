package com.tec.dnsapi.exception;

/** Petición con datos inválidos; se responde con 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
