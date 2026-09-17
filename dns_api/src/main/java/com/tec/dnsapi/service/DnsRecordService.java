package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.ExistsResponse;
import com.tec.dnsapi.repository.DnsRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DnsRecordService {

    private final DnsRecordRepository repository;

    public DnsRecordService(DnsRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ExistsResponse findByName(String name) {
        String normalized = normalize(name);
        return repository.findById(normalized)
                .map(r -> new ExistsResponse(true, r.getName(), r.getType(), r.getTtl(), r.getIps()))
                .orElseGet(() -> ExistsResponse.notFound(normalized));
    }

    private String normalize(String name) {
        if (name == null) return "";
        String trimmed = name.trim().toLowerCase();
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}