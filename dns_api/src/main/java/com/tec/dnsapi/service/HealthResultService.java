package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.HealthResultResponse;
import com.tec.dnsapi.model.HealthResult;
import com.tec.dnsapi.repository.HealthResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HealthResultService {

    private final HealthResultRepository healthResultRepository;

    public HealthResultService(HealthResultRepository healthResultRepository) {
        this.healthResultRepository = healthResultRepository;
    }

    public List<HealthResultResponse> findAll() {
        return healthResultRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<HealthResultResponse> findByRecordName(String recordName) {
        return healthResultRepository.findByRecordNameOrderByCheckedAtDesc(recordName).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<HealthResultResponse> findByTargetId(UUID targetId) {
        return healthResultRepository.findByTargetIdOrderByCheckedAtDesc(targetId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(Long id) {
        if (!healthResultRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "HealthResult not found");
        }
        healthResultRepository.deleteById(id);
    }

    @Transactional
    public void deleteByRecordName(String recordName) {
        healthResultRepository.deleteByRecordName(recordName);
    }

    private HealthResultResponse toResponse(HealthResult r) {
        return new HealthResultResponse(
                r.getId(),
                r.getTargetId(),
                r.getRecordName(),
                r.getIpAddress(),
                r.getIsHealthy(),
                r.getLatencyMs(),
                r.getCheckerLocationId(),
                r.getCheckerLatitude(),
                r.getCheckerLongitude(),
                r.getCheckerCountry(),
                r.getCheckerCity(),
                r.getCheckedAt() != null ? r.getCheckedAt().toString() : null);
    }
}
