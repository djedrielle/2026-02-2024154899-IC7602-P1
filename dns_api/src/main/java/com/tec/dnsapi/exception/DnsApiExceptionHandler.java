package com.tec.dnsapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DnsApiExceptionHandler {

    @ExceptionHandler(DnsResolutionException.class)
    public ResponseEntity<String> handleDnsResolution(DnsResolutionException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
    }
}
