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

    protected DnsRecord() {}

    public String getName() { return name; }
    public String getType() { return type; }
    public Integer getTtl() { return ttl; }
    public List<Map<String, Object>> getIps() { return ips; }
}