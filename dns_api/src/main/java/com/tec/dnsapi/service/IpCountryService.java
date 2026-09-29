package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.IpCountryResponse;
import com.tec.dnsapi.dto.IpToCountryFullResponse;
import com.tec.dnsapi.dto.IpToCountryRequest;
import com.tec.dnsapi.exception.InvalidRequestException;
import com.tec.dnsapi.model.IpToCountry;
import com.tec.dnsapi.validation.IpAddresses;
import com.tec.dnsapi.validation.IpCountryValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        if (!IpAddresses.isValid(ip)) {
            throw new InvalidRequestException("'ip' no es una IP válida: " + ip);
        }
        return repository.findByIp(ip.trim())
                .<Object>map(r -> new IpCountryResponse(r.getCountryCode()))
                .orElse(false);
    }

    // -------------------------------------------------------------------------
    // CRUD para la DNS UI: GET/POST/PUT/DELETE /api/ip_country
    // -------------------------------------------------------------------------

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 1000;

    /** La tabla tiene cientos de miles de rangos: el listado siempre es paginado y ordenado por id. */
    @Transactional(readOnly = true)
    public List<IpToCountryFullResponse> findPage(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("'page' no puede ser negativo");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("'size' debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        return repository.findAllBy(PageRequest.of(page, size, Sort.by("id"))).stream()
                .map(this::toFullResponse)
                .toList();
    }

    @Transactional
    public IpToCountryFullResponse create(IpToCountryRequest req) {
        IpCountryValidator.validate(req);
        IpToCountry saved = repository.save(new IpToCountry(
                req.start_ip().trim(), req.end_ip().trim(), req.country_code().toUpperCase(),
                req.country_name(), req.city(), req.latitude(), req.longitude()));
        return toFullResponse(saved);
    }

    @Transactional
    public IpToCountryFullResponse update(Long id, IpToCountryRequest req) {
        IpCountryValidator.validate(req);
        IpToCountry record = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe un registro ip_to_country con id: " + id));

        record.setStartIp(req.start_ip().trim());
        record.setEndIp(req.end_ip().trim());
        record.setCountryCode(req.country_code().toUpperCase());
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