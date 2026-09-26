package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.RecordRequest;
import com.tec.dnsapi.dto.RecordResponse;
import com.tec.dnsapi.model.DnsRecord;
import com.tec.dnsapi.repository.DnsRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DnsRecordService {

    private final DnsRecordRepository repository;

    public DnsRecordService(DnsRecordRepository repository) {
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // Usado por el DNS Interceptor: GET /api/exists
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Object findByDomain(String domain) {
        String normalized = normalize(domain);
        return repository.findById(normalized)
                .<Object>map(r -> new RecordResponse(
                        r.getName(), r.getType(), r.getTtl(), r.getIps()))
                .orElse(false);
    }

    // -------------------------------------------------------------------------
    // CRUD para la DNS UI: GET/POST/PUT/DELETE /api/records
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RecordResponse> findAll() {
        return repository.findAll().stream()
                .map(r -> new RecordResponse(r.getName(), r.getType(), r.getTtl(), r.getIps()))
                .toList();
    }

    @Transactional
    public RecordResponse create(RecordRequest req) {
        String name = normalize(req.name());
        if (repository.existsById(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ya existe un registro con el dominio: " + name);
        }
        DnsRecord saved = repository.save(
                new DnsRecord(name, req.type(), req.ttl(), req.ips()));
        return new RecordResponse(saved.getName(), saved.getType(), saved.getTtl(), saved.getIps());
    }

    @Transactional
    public RecordResponse update(String name, RecordRequest req) {
        String normalized = normalize(name);
        DnsRecord record = repository.findById(normalized)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe un registro con el dominio: " + normalized));

        record.setType(req.type());
        record.setTtl(req.ttl());
        record.setIps(req.ips());

        DnsRecord saved = repository.save(record);
        return new RecordResponse(saved.getName(), saved.getType(), saved.getTtl(), saved.getIps());
    }

    @Transactional
    public void delete(String name) {
        String normalized = normalize(name);
        if (!repository.existsById(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No existe un registro con el dominio: " + normalized);
        }
        repository.deleteById(normalized);
    }

    // -------------------------------------------------------------------------

    private String normalize(String domain) {
        if (domain == null) return "";
        String trimmed = domain.trim().toLowerCase();
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}