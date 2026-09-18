package com.tec.dnsapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class DnsApiExceptionHandler {

    @ExceptionHandler(InvalidDnsPacketException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPacket(
            InvalidDnsPacketException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DnsResolutionException.class)
    public ResponseEntity<Map<String, String>> handleDnsResolution(
            DnsResolutionException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<Map<String, String>> handleOverload(
            RejectedExecutionException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Servidor saturado, intente de nuevo"));
    }

    /**
     * Las excepciones lanzadas dentro del CompletableFuture llegan aqui
     * envueltas en CompletionException, por lo que hay que desempaquetarlas
     * para responder con el status correcto.
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, String>> handleCompletion(CompletionException e) {
        Throwable cause = e.getCause();

        if (cause instanceof DnsResolutionException) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", cause.getMessage()));
        }
        if (cause instanceof RejectedExecutionException) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Servidor saturado, intente de nuevo"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error inesperado al procesar la solicitud"));
    }
}