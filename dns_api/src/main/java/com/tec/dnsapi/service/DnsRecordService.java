package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.RecordResponse;
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
    public Object findByDomain(String domain) {
        String normalized = normalize(domain);
        return repository.findById(normalized)
                .<Object>map(r -> new RecordResponse(
                        r.getName(), r.getType(), r.getTtl(), r.getIps()))
                .orElse(false);
    }

    private String normalize(String domain) {
        if (domain == null) return "";
        String trimmed = domain.trim().toLowerCase();
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}