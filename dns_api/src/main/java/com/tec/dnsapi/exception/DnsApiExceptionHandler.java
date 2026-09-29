package com.tec.dnsapi.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

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
         * Maneja 404, 409 y cualquier otro status lanzado desde los servicios
         * via ResponseStatusException (usado en el CRUD de /api/records).
         */
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<Map<String, String>> handleResponseStatus(
                        ResponseStatusException e) {
                return ResponseEntity.status(e.getStatusCode())
                                .body(Map.of("error", e.getReason() != null ? e.getReason() : e.getMessage()));
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

        /** Datos que no cumplen las reglas de validación de la API. */
        @ExceptionHandler(InvalidRequestException.class)
        public ResponseEntity<Map<String, String>> handleInvalidRequest(InvalidRequestException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", e.getMessage()));
        }

        /**
         * Restricciones de la base (unicidad, llaves foráneas, checks). No se expone el
         * mensaje SQL: se traduce a 409 (duplicado) o 400 (referencia/valor inválido).
         */
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
                String detail = e.getMostSpecificCause().getMessage();
                String lower = detail == null ? "" : detail.toLowerCase();
                if (lower.contains("duplicate key")) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                        .body(Map.of("error", "Ya existe un elemento con esos datos"));
                }
                if (lower.contains("foreign key")) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(Map.of("error", "Hace referencia a un elemento que no existe"));
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "Los datos no cumplen las restricciones de la base de datos"));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "El cuerpo de la petición no es un JSON válido"));
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<Map<String, String>> handleMissingParameter(MissingServletRequestParameterException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "Falta el parámetro obligatorio: " + e.getParameterName()));
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "Valor inválido para el parámetro: " + e.getName()));
        }
}
