package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.LatencyResponse;
import com.tec.dnsapi.service.LatencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LatencyController {

    private final LatencyService service;

    public LatencyController(LatencyService service) {
        this.service = service;
    }

    /**
     * Datos crudos de latencia para un registro round-trip.
     *
     * Devuelve la medición más reciente de cada (checker, IP); lista vacía si no
     * hay datos. El DNS Interceptor elige el checker más cercano al cliente y la
     * IP de menor latencia.
     */
    @GetMapping("/latency")
    public ResponseEntity<List<LatencyResponse>> latency(@RequestParam String domain) {
        return ResponseEntity.ok(service.findLatestByDomain(domain));
    }
}
