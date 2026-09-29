package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.LatencyResponse;
import com.tec.dnsapi.model.HealthResult;
import com.tec.dnsapi.repository.HealthResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LatencyService {

    private final HealthResultRepository repository;

    public LatencyService(HealthResultRepository repository) {
        this.repository = repository;
    }

    /**
     * Usado por el DNS Interceptor: GET /api/latency?domain=X
     *
     * Devuelve la medición MÁS RECIENTE de cada combinación (checker, IP) para
     * el registro dado. Son datos crudos: el interceptor decide el checker más
     * cercano al cliente y la IP de menor latencia. Lista vacía si no hay datos.
     */
    @Transactional(readOnly = true)
    public List<LatencyResponse> findLatestByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return List.of();
        }

        // El repositorio devuelve las filas ordenadas por checked_at DESC, así
        // que la PRIMERA aparición de cada (checker, IP) es la más reciente.
        List<HealthResult> results =
                repository.findByRecordNameOrderByCheckedAtDesc(domain.trim());

        Set<String> vistos = new HashSet<>();
        List<LatencyResponse> ultimas = new ArrayList<>();
        for (HealthResult r : results) {
            String clave = r.getCheckerLocationId() + "|" + r.getIpAddress();
            if (vistos.add(clave)) {
                ultimas.add(new LatencyResponse(
                        r.getIpAddress(),
                        r.getLatencyMs(),
                        r.getIsHealthy(),
                        r.getCheckerLocationId(),
                        r.getCheckerLatitude(),
                        r.getCheckerLongitude(),
                        r.getCheckerCountry(),
                        r.getCheckerCity(),
                        r.getCheckedAt()
                ));
            }
        }
        return ultimas;
    }
}
