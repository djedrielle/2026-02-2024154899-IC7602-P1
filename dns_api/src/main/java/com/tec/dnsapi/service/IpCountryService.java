package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.IpCountryResponse;
import com.tec.dnsapi.dto.IpToCountryFullResponse;
import com.tec.dnsapi.dto.IpToCountryRequest;
import com.tec.dnsapi.model.IpToCountry;
import com.tec.dnsapi.repository.IpToCountryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class IpCountryService {

    private final IpToCountryRepository repository;

    public IpCountryService(IpToCountryRepository repository) {
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // Usado por el DNS Interceptor: GET /api/ip_country?ip=X
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Object findCountryByIp(String ip) {
        if (ip == null || ip.isBlank())
            return false;
        return repository.findByIp(ip.trim())
                .<Object>map(r -> new IpCountryResponse(r.getCountryCode()))
                .orElse(false);
    }

    // -------------------------------------------------------------------------
// CRUD para la DNS UI: GET/POST/PUT/DELETE /api/ip_country
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<IpToCountryFullResponse> findAll() {
        return repository.findAll().stream()
                .map(this::toFullResponse)
                .toList();
    }

    @Transactional
    public IpToCountryFullResponse create(IpToCountryRequest req) {
        IpToCountry saved = repository.save(new IpToCountry(
                req.start_ip(), req.end_ip(), req.country_code(),
                req.country_name(), req.city(), req.latitude(), req.longitude()));
        return toFullResponse(saved);
    }

    @Transactional
    public IpToCountryFullResponse update(Long id, IpToCountryRequest req) {
        IpToCountry record = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe un registro ip_to_country con id: " + id));

        record.setStartIp(req.start_ip());
        record.setEndIp(req.end_ip());
        record.setCountryCode(req.country_code());
        record.setCountryName(req.country_name());
        record.setCity(req.city());
        record.setLatitude(req.latitude());
        record.setLongitude(req.longitude());

        return toFullResponse(repository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No existe un registro ip_to_country con id: " + id);
        }
        repository.deleteById(id);
    }

    // -------------------------------------------------------------------------

    private IpToCountryFullResponse toFullResponse(IpToCountry r) {
        return new IpToCountryFullResponse(
                r.getId(), r.getStartIp(), r.getEndIp(),
                r.getCountryCode(), r.getCountryName(), r.getCity(),
                r.getLatitude(), r.getLongitude());
    }
}