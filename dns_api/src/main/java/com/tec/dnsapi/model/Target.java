package com.tec.dnsapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "targets")
public class Target {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "record_name")
    private String recordName;

    @Column(name = "ip_address")
    private String ipAddress;

    private Integer port;

    @Column(name = "check_type")
    private String checkType;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    private Integer retries;

    @Column(name = "http_path")
    private String httpPath;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "expected_status_codes", columnDefinition = "integer[]")
    private List<Integer> expectedStatusCodes;

    @Column(name = "basic_auth_user")
    private String basicAuthUser;

    @Column(name = "basic_auth_pass")
    private String basicAuthPass;

    protected Target() {}

    public Target(String recordName, String ipAddress, Integer port, String checkType, Integer timeoutMs, Integer retries, String httpPath, List<Integer> expectedStatusCodes, String basicAuthUser, String basicAuthPass) {
        this.recordName = recordName;
        this.ipAddress = ipAddress;
        this.port = port;
        this.checkType = checkType;
        this.timeoutMs = timeoutMs;
        this.retries = retries;
        this.httpPath = httpPath;
        this.expectedStatusCodes = expectedStatusCodes;
        this.basicAuthUser = basicAuthUser;
        this.basicAuthPass = basicAuthPass;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public String getHttpPath() {
        return httpPath;
    }

    public void setHttpPath(String httpPath) {
        this.httpPath = httpPath;
    }

    public List<Integer> getExpectedStatusCodes() {
        return expectedStatusCodes;
    }

    public void setExpectedStatusCodes(List<Integer> expectedStatusCodes) {
        this.expectedStatusCodes = expectedStatusCodes;
    }

    public String getBasicAuthUser() {
        return basicAuthUser;
    }

    public void setBasicAuthUser(String basicAuthUser) {
        this.basicAuthUser = basicAuthUser;
    }

    public String getBasicAuthPass() {
        return basicAuthPass;
    }

    public void setBasicAuthPass(String basicAuthPass) {
        this.basicAuthPass = basicAuthPass;
    }
}
