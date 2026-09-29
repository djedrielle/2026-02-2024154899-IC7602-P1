package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.RecordRequest;
import com.tec.dnsapi.dto.RecordResponse;
import com.tec.dnsapi.model.DnsRecord;
import com.tec.dnsapi.repository.DnsRecordRepository;
import com.tec.dnsapi.validation.DomainNames;
import com.tec.dnsapi.validation.RecordValidator;
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

    /**
     * Busca un registro por dominio.
     * Para registros de tipo "multi", incrementa el counter atómicamente
     * antes de devolver la respuesta, de forma que el Interceptor pueda
     * aplicar round-robin usando counter % len(healthy_ips).
     */
    @Transactional
    public Object findByDomain(String domain) {
        String normalized = DomainNames.normalize(domain);
        return repository.findById(normalized)
                .<Object>map(r -> {
                    if ("multi".equals(r.getType())) {
                        repository.incrementCounter(normalized);
                        // Refrescar el valor actualizado del counter
                        DnsRecord updated = repository.findById(normalized).orElse(r);
                        return toResponse(updated);
                    }
                    return toResponse(r);
                })
                .orElse(false);
    }

    // -------------------------------------------------------------------------
    // CRUD para la DNS UI: GET/POST/PUT/DELETE /api/records
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RecordResponse> findAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RecordResponse create(RecordRequest req) {
        RecordValidator.validate(req, true);
        String name = DomainNames.normalize(req.name());
        if (repository.existsById(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ya existe un registro con el dominio: " + name);
        }
        DnsRecord saved = repository.save(
                new DnsRecord(name, req.type(), req.ttl(), req.ips()));
        return toResponse(saved);
    }

    @Transactional
    public RecordResponse update(String name, RecordRequest req) {
        RecordValidator.validate(req, false);
        String normalized = DomainNames.normalize(name);
        DnsRecord record = repository.findById(normalized)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe un registro con el dominio: " + normalized));

        record.setType(req.type());
        record.setTtl(req.ttl());
        record.setIps(req.ips());
        // counter es NOT NULL: los tipos que no son multi lo dejan en 0
        if (!"multi".equals(req.type()) || record.getCounter() == null) {
            record.setCounter(0);
        }

        return toResponse(repository.save(record));
    }

    @Transactional
    public void delete(String name) {
        String normalized = DomainNames.normalize(name);
        if (!repository.existsById(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No existe un registro con el dominio: " + normalized);
        }
        repository.deleteById(normalized);
    }

    // -------------------------------------------------------------------------

    private RecordResponse toResponse(DnsRecord r) {
        return new RecordResponse(
                r.getName(), r.getType(), r.getTtl(), r.getIps(), r.getCounter());
    }
}