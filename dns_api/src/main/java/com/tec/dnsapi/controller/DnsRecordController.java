package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.RecordRequest;
import com.tec.dnsapi.dto.RecordResponse;
import com.tec.dnsapi.service.DnsRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DnsRecordController {

    private final DnsRecordService service;

    public DnsRecordController(DnsRecordService service) {
        this.service = service;
    }

    // -------------------------------------------------------------------------
    // Usado por el DNS Interceptor
    // -------------------------------------------------------------------------

    /** Devuelve el registro completo si existe, o false si no. */
    @GetMapping("/exists")
    public ResponseEntity<Object> exists(@RequestParam String domain) {
        return ResponseEntity.ok(service.findByDomain(domain));
    }

    // -------------------------------------------------------------------------
    // CRUD para la DNS UI
    // -------------------------------------------------------------------------

    /** Lista todos los registros DNS. */
    @GetMapping("/records")
    public ResponseEntity<List<RecordResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    /** Crea un nuevo registro DNS. Devuelve 201 + el registro creado. */
    @PostMapping("/records")
    public ResponseEntity<RecordResponse> create(@RequestBody RecordRequest request) {
        RecordResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Actualiza un registro existente. Devuelve el registro actualizado. */
    @PutMapping("/records/{name}")
    public ResponseEntity<RecordResponse> update(
            @PathVariable String name,
            @RequestBody RecordRequest request) {
        return ResponseEntity.ok(service.update(name, request));
    }

    /** Elimina un registro. Devuelve 204 sin cuerpo. */
    @DeleteMapping("/records/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        service.delete(name);
        return ResponseEntity.noContent().build();
    }
}