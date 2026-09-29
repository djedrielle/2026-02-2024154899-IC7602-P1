package com.tec.dnsapi.repository;

import com.tec.dnsapi.model.DnsRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DnsRecordRepository extends JpaRepository<DnsRecord, String> {

    /**
     * Incrementa atómicamente el counter del registro en PostgreSQL.
     * Usar solo para registros de tipo "multi".
     * Se ejecuta como UPDATE para garantizar atomicidad sin race conditions.
     */
    @Modifying
    @Query(value = "UPDATE records SET counter = counter + 1 WHERE name = :name", nativeQuery = true)
    void incrementCounter(@Param("name") String name);
}