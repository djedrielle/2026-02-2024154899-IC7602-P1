package com.tec.dnsapi.repository;

import com.tec.dnsapi.model.DnsRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DnsRecordRepository extends JpaRepository<DnsRecord, String> {
}