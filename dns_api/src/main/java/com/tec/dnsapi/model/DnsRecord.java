package com.tec.dnsapi.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "records")
public class DnsRecord {

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "ttl", nullable = false)
    private Integer ttl;

    @Type(JsonBinaryType.class)
    @Column(name = "ips", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> ips;

    protected DnsRecord() {
    }

    public DnsRecord(String name, String type, Integer ttl, List<Map<String, Object>> ips) {
        this.name = name;
        this.type = type;
        this.ttl = ttl;
        this.ips = ips;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Integer getTtl() {
        return ttl;
    }

    public List<Map<String, Object>> getIps() {
        return ips;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public void setIps(List<Map<String, Object>> ips) {
        this.ips = ips;
    }
}