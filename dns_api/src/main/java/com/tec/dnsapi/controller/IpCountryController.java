package com.tec.dnsapi.controller;

import com.tec.dnsapi.service.IpCountryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class IpCountryController {

    private final IpCountryService service;

    public IpCountryController(IpCountryService service) {
        this.service = service;
    }

    @GetMapping("/ip_country")
    public ResponseEntity<Object> ipCountry(@RequestParam String ip) {
        return ResponseEntity.ok(service.findCountryByIp(ip));
    }
}