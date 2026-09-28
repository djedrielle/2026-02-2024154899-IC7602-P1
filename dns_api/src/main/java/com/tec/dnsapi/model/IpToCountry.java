package com.tec.dnsapi.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ip_to_country")
public class IpToCountry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "start_ip", nullable = false, columnDefinition = "inet")
    private String startIp;

    @Column(name = "end_ip", nullable = false, columnDefinition = "inet")
    private String endIp;

    @Column(name = "country_code", nullable = false, columnDefinition = "bpchar")
    private String countryCode;

    @Column(name = "country_name")
    private String countryName;

    @Column(name = "city")
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    protected IpToCountry() {
    }

    public IpToCountry(String startIp, String endIp, String countryCode,
            String countryName, String city,
            Double latitude, Double longitude) {
        this.startIp = startIp;
        this.endIp = endIp;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return id;
    }

    public String getStartIp() {
        return startIp;
    }

    public String getEndIp() {
        return endIp;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public String getCity() {
        return city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setStartIp(String startIp) {
        this.startIp = startIp;
    }

    public void setEndIp(String endIp) {
        this.endIp = endIp;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}