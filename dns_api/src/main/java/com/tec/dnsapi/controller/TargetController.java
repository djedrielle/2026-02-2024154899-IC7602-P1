package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.TargetRequest;
import com.tec.dnsapi.dto.TargetResponse;
import com.tec.dnsapi.service.TargetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TargetController {

    private final TargetService targetService;

    public TargetController(TargetService targetService) {
        this.targetService = targetService;
    }

    @GetMapping("/targets")
    public ResponseEntity<List<TargetResponse>> getTargets(@RequestParam(value = "record_name", required = false) String recordName) {
        if (recordName != null && !recordName.isEmpty()) {
            return ResponseEntity.ok(targetService.findByRecordName(recordName));
        }
        return ResponseEntity.ok(targetService.findAll());
    }

    @PostMapping("/targets")
    public ResponseEntity<TargetResponse> createTarget(@RequestBody TargetRequest request) {
        TargetResponse response = targetService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/targets/{id}")
    public ResponseEntity<TargetResponse> updateTarget(@PathVariable UUID id, @RequestBody TargetRequest request) {
        TargetResponse response = targetService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/targets/{id}")
    public ResponseEntity<Void> deleteTarget(@PathVariable UUID id) {
        targetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/targets")
    public ResponseEntity<Void> deleteTargetsByRecordName(@RequestParam("record_name") String recordName) {
        targetService.deleteByRecordName(recordName);
        return ResponseEntity.noContent().build();
    }
}
