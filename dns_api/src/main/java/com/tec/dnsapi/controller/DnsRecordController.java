package com.tec.dnsapi.controller;

import com.tec.dnsapi.service.DnsRecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DnsRecordController {

    private final DnsRecordService service;

    public DnsRecordController(DnsRecordService service) {
        this.service = service;
    }

    @GetMapping("/exists")
    public ResponseEntity<Object> exists(@RequestParam String domain) {
        return ResponseEntity.ok(service.findByDomain(domain));
    }
}