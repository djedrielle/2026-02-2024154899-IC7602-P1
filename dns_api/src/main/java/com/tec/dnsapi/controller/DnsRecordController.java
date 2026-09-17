package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.ExistsResponse;
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
    public ResponseEntity<ExistsResponse> exists(@RequestParam String name) {
        return ResponseEntity.ok(service.findByName(name));
    }
}