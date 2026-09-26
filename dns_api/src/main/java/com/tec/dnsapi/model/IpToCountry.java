package com.tec.dnsapi.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ip_to_country")
public class IpToCountry {

    @Id
    @Column(name = "id")
    private Long id;

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

    protected IpToCountry() {}

    public Long getId() { return id; }
    public String getCountryCode() { return countryCode; }
    public String getCountryName() { return countryName; }
    public String getCity() { return city; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}