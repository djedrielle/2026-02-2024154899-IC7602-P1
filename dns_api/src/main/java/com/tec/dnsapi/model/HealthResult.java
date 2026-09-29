package com.tec.dnsapi.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "health_results")
public class HealthResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "record_name")
    private String recordName;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "is_healthy")
    private Boolean isHealthy;

    @Column(name = "latency_ms")
    private Double latencyMs;

    @Column(name = "checker_location_id")
    private String checkerLocationId;

    @Column(name = "checker_latitude")
    private Double checkerLatitude;

    @Column(name = "checker_longitude")
    private Double checkerLongitude;

    @Column(name = "checker_country")
    private String checkerCountry;

    @Column(name = "checker_city")
    private String checkerCity;

    @Column(name = "checked_at")
    private Instant checkedAt;

    protected HealthResult() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }
    public String getRecordName() { return recordName; }
    public void setRecordName(String recordName) { this.recordName = recordName; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Boolean getIsHealthy() { return isHealthy; }
    public void setIsHealthy(Boolean isHealthy) { this.isHealthy = isHealthy; }
    public Double getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Double latencyMs) { this.latencyMs = latencyMs; }
    public String getCheckerLocationId() { return checkerLocationId; }
    public void setCheckerLocationId(String checkerLocationId) { this.checkerLocationId = checkerLocationId; }
    public Double getCheckerLatitude() { return checkerLatitude; }
    public void setCheckerLatitude(Double checkerLatitude) { this.checkerLatitude = checkerLatitude; }
    public Double getCheckerLongitude() { return checkerLongitude; }
    public void setCheckerLongitude(Double checkerLongitude) { this.checkerLongitude = checkerLongitude; }
    public String getCheckerCountry() { return checkerCountry; }
    public void setCheckerCountry(String checkerCountry) { this.checkerCountry = checkerCountry; }
    public String getCheckerCity() { return checkerCity; }
    public void setCheckerCity(String checkerCity) { this.checkerCity = checkerCity; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
}
