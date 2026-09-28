package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.TargetRequest;
import com.tec.dnsapi.dto.TargetResponse;
import com.tec.dnsapi.model.Target;
import com.tec.dnsapi.repository.TargetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TargetService {

    private final TargetRepository targetRepository;

    public TargetService(TargetRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    public List<TargetResponse> findAll() {
        return targetRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<TargetResponse> findByRecordName(String recordName) {
        return targetRepository.findByRecordName(recordName).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TargetResponse create(TargetRequest request) {
        Target target = new Target(
                request.record_name(),
                request.ip_address(),
                request.port(),
                request.check_type(),
                request.timeout_ms(),
                request.retries(),
                request.http_path(),
                request.expected_status_codes(),
                request.basic_auth_user(),
                request.basic_auth_pass()
        );
        Target savedTarget = targetRepository.save(target);
        return mapToResponse(savedTarget);
    }

    @Transactional
    public TargetResponse update(UUID id, TargetRequest request) {
        Target target = targetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not found"));
        
        target.setRecordName(request.record_name());
        target.setIpAddress(request.ip_address());
        target.setPort(request.port());
        target.setCheckType(request.check_type());
        target.setTimeoutMs(request.timeout_ms());
        target.setRetries(request.retries());
        target.setHttpPath(request.http_path());
        target.setExpectedStatusCodes(request.expected_status_codes());
        target.setBasicAuthUser(request.basic_auth_user());
        target.setBasicAuthPass(request.basic_auth_pass());

        Target updatedTarget = targetRepository.save(target);
        return mapToResponse(updatedTarget);
    }

    @Transactional
    public void delete(UUID id) {
        if (!targetRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not found");
        }
        targetRepository.deleteById(id);
    }

    @Transactional
    public void deleteByRecordName(String recordName) {
        targetRepository.deleteByRecordName(recordName);
    }

    private TargetResponse mapToResponse(Target target) {
        return new TargetResponse(
                target.getId(),
                target.getRecordName(),
                target.getIpAddress(),
                target.getPort(),
                target.getCheckType(),
                target.getTimeoutMs(),
                target.getRetries(),
                target.getHttpPath(),
                target.getExpectedStatusCodes(),
                target.getBasicAuthUser(),
                target.getBasicAuthPass()
        );
    }
}
