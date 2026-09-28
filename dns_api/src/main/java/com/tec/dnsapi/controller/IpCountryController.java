package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.IpToCountryFullResponse;
import com.tec.dnsapi.dto.IpToCountryRequest;
import com.tec.dnsapi.service.IpCountryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class IpCountryController {

    private final IpCountryService service;

    public IpCountryController(IpCountryService service) {
        this.service = service;
    }

    // -------------------------------------------------------------------------
    // Usado por el DNS Interceptor: lookup de IP → país
    // -------------------------------------------------------------------------

    /**
     * Con ?ip=X → devuelve {"country_code":"CR"} o false (Interceptor)
     * Sin param → devuelve la lista completa List<IpToCountryFullResponse> (DNS UI)
     */
    @GetMapping("/ip_country")
    public ResponseEntity<Object> ipCountry(
            @RequestParam(required = false) String ip) {

        if (ip != null && !ip.isBlank()) {
            return ResponseEntity.ok(service.findCountryByIp(ip.trim()));
        }
        return ResponseEntity.ok(service.findAll());
    }

    // -------------------------------------------------------------------------
    // CRUD para la DNS UI
    // -------------------------------------------------------------------------

    /** Crea un rango IP → país. Devuelve 201 con el registro creado. */
    @PostMapping("/ip_country")
    public ResponseEntity<IpToCountryFullResponse> create(
            @RequestBody IpToCountryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    /** Actualiza un rango existente por id. */
    @PutMapping("/ip_country/{id}")
    public ResponseEntity<IpToCountryFullResponse> update(
            @PathVariable Long id,
            @RequestBody IpToCountryRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    /** Elimina un rango por id. Devuelve 204 sin cuerpo. */
    @DeleteMapping("/ip_country/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}