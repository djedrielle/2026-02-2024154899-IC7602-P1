package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.IpCountryResponse;
import com.tec.dnsapi.repository.IpToCountryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IpCountryService {

    private final IpToCountryRepository repository;

    public IpCountryService(IpToCountryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Object findCountryByIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return repository.findByIp(ip.trim())
                .<Object>map(r -> new IpCountryResponse(r.getCountryCode()))
                .orElse(false);
    }
}