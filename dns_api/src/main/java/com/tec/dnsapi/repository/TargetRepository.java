package com.tec.dnsapi.repository;

import com.tec.dnsapi.model.Target;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TargetRepository extends JpaRepository<Target, UUID> {
    List<Target> findByRecordName(String recordName);
    void deleteByRecordName(String recordName);
}
