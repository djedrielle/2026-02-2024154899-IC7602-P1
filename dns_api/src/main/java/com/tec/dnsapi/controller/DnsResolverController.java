package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.DnsResolverRequest;
import com.tec.dnsapi.dto.DnsResolverResponse;
import com.tec.dnsapi.service.DnsResolverService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class DnsResolverController {

    private final DnsResolverService service;

    public DnsResolverController(DnsResolverService service) {
        this.service = service;
    }

    @PostMapping("/dns_resolver")
    public CompletableFuture<ResponseEntity<DnsResolverResponse>> resolve(
            @RequestBody DnsResolverRequest request) {

        return CompletableFuture.supplyAsync(() -> service.resolve(request))
                .thenApply(ResponseEntity::ok);
    }
}