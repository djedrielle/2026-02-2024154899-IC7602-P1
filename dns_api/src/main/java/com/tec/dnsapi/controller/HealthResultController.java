package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.HealthResultResponse;
import com.tec.dnsapi.service.HealthResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/health_results")
public class HealthResultController {

    private final HealthResultService healthResultService;

    public HealthResultController(HealthResultService healthResultService) {
        this.healthResultService = healthResultService;
    }

    @GetMapping
    public ResponseEntity<List<HealthResultResponse>> list(
            @RequestParam(name = "record_name", required = false) String recordName,
            @RequestParam(name = "target_id", required = false) UUID targetId) {
        if (recordName != null) {
            return ResponseEntity.ok(healthResultService.findByRecordName(recordName));
        } else if (targetId != null) {
            return ResponseEntity.ok(healthResultService.findByTargetId(targetId));
        }
        return ResponseEntity.ok(healthResultService.findAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        healthResultService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteByRecordName(@RequestParam(name = "record_name") String recordName) {
        healthResultService.deleteByRecordName(recordName);
        return ResponseEntity.noContent().build();
    }
}
