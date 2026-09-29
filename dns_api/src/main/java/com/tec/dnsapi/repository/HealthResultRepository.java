package com.tec.dnsapi.repository;

import com.tec.dnsapi.model.HealthResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HealthResultRepository extends JpaRepository<HealthResult, Long> {
    List<HealthResult> findByRecordNameOrderByCheckedAtDesc(String recordName);
    List<HealthResult> findByTargetIdOrderByCheckedAtDesc(UUID targetId);
    void deleteByRecordName(String recordName);
}
