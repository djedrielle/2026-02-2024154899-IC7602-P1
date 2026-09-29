package com.tec.dnsapi.repository;

import com.tec.dnsapi.model.IpToCountry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IpToCountryRepository extends JpaRepository<IpToCountry, Long> {

    @Query(value = """
            SELECT * FROM ip_to_country
            WHERE CAST(:ip AS inet) >= start_ip
              AND CAST(:ip AS inet) <= end_ip
            LIMIT 1
            """, nativeQuery = true)
    Optional<IpToCountry> findByIp(@Param("ip") String ip);

    /** Devuelve una página sin ejecutar COUNT(*) sobre toda la tabla. */
    Slice<IpToCountry> findAllBy(Pageable pageable);
}
