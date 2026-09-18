package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.DnsResolverRequest;
import com.tec.dnsapi.dto.DnsResolverResponse;
import com.tec.dnsapi.service.DnsResolverService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api")
public class DnsResolverController {

    private final DnsResolverService service;
    private final ExecutorService executor;

    public DnsResolverController(
            DnsResolverService service,
            @Qualifier("dnsResolverExecutor") ExecutorService executor) {
        this.service = service;
        this.executor = executor;
    }

    @PostMapping("/dns_resolver")
    public CompletableFuture<ResponseEntity<DnsResolverResponse>> resolve(
            @RequestBody(required = false) DnsResolverRequest request) {

        byte[] rawPacket = service.decodeAndValidate(request);

        return CompletableFuture
                .supplyAsync(() -> service.resolveDecoded(rawPacket), executor)
                .thenApply(ResponseEntity::ok);
    }
}