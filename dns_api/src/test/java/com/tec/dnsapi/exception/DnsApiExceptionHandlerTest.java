package com.tec.dnsapi.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DnsApiExceptionHandler")
class DnsApiExceptionHandlerTest {

    private final DnsApiExceptionHandler handler = new DnsApiExceptionHandler();

    private static void assertResponse(ResponseEntity<Map<String, String>> response, HttpStatus status, String error) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).containsEntry("error", error);
    }

    @Test
    @DisplayName("paquete inválido -> 400, fallo del DNS remoto -> 502, saturación -> 503")
    void dnsErrors() {
        assertResponse(handler.handleInvalidPacket(new InvalidDnsPacketException("mal")), HttpStatus.BAD_REQUEST, "mal");
        assertResponse(handler.handleDnsResolution(new DnsResolutionException("upstream", new IOException())),
                HttpStatus.BAD_GATEWAY, "upstream");
        assertResponse(handler.handleOverload(new RejectedExecutionException()),
                HttpStatus.SERVICE_UNAVAILABLE, "Servidor saturado, intente de nuevo");
    }

    @Test
    @DisplayName("ResponseStatusException conserva su estado; sin razón usa el mensaje")
    void responseStatus() {
        assertResponse(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "no existe")),
                HttpStatus.NOT_FOUND, "no existe");
        ResponseEntity<Map<String, String>> withoutReason = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.CONFLICT));
        assertThat(withoutReason.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(withoutReason.getBody().get("error")).contains("409");
    }

    @Test
    @DisplayName("CompletionException se desempaqueta según su causa")
    void completionException() {
        assertResponse(handler.handleCompletion(new CompletionException(new DnsResolutionException("x", new IOException()))),
                HttpStatus.BAD_GATEWAY, "x");
        assertResponse(handler.handleCompletion(new CompletionException(new RejectedExecutionException())),
                HttpStatus.SERVICE_UNAVAILABLE, "Servidor saturado, intente de nuevo");
        assertResponse(handler.handleCompletion(new CompletionException(new IllegalStateException("boom"))),
                HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado al procesar la solicitud");
    }

    @Test
    @DisplayName("InvalidRequestException -> 400")
    void invalidRequest() {
        assertResponse(handler.handleInvalidRequest(new InvalidRequestException("dato inválido")),
                HttpStatus.BAD_REQUEST, "dato inválido");
    }

    @Test
    @DisplayName("violaciones de integridad no exponen el SQL")
    void dataIntegrityHidesSql() {
        ResponseEntity<Map<String, String>> duplicate = handler
                .handleDataIntegrity(new DataIntegrityViolationException("x", new RuntimeException("DUPLICATE KEY value violates unique")));
        ResponseEntity<Map<String, String>> nullCause = handler.handleDataIntegrity(new DataIntegrityViolationException("x"));

        assertResponse(duplicate, HttpStatus.CONFLICT, "Ya existe un elemento con esos datos");
        assertThat(nullCause.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(nullCause.getBody().get("error")).doesNotContain("SQL");
    }
}
