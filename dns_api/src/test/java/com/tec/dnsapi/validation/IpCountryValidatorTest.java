package com.tec.dnsapi.validation;

import com.tec.dnsapi.dto.IpToCountryRequest;
import com.tec.dnsapi.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IpCountryValidator")
class IpCountryValidatorTest {

    private static IpToCountryRequest req(String start, String end, String code, Double lat, Double lon) {
        return new IpToCountryRequest(start, end, code, "Costa Rica", "San José", lat, lon);
    }

    private static void assertInvalid(IpToCountryRequest request, String messagePart) {
        assertThatThrownBy(() -> IpCountryValidator.validate(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    @DisplayName("acepta rangos IPv4 e IPv6 válidos, incluido un rango de una sola IP")
    void acceptsValidRanges() {
        assertThatCode(() -> {
            IpCountryValidator.validate(req("10.0.0.0", "10.0.0.255", "CR", 9.9, -84.0));
            IpCountryValidator.validate(req("10.0.0.5", "10.0.0.5", "cr", null, null));
            IpCountryValidator.validate(req("2001:db8::", "2001:db8::ffff", "US", null, null));
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rechaza IPs faltantes, inválidas o de distinta familia")
    void rejectsBadIps() {
        assertInvalid(null, "obligatorio");
        assertInvalid(req(null, "10.0.0.5", "CR", null, null), "'start_ip'");
        assertInvalid(req("basura", "10.0.0.5", "CR", null, null), "'start_ip'");
        assertInvalid(req("10.0.0.1", null, "CR", null, null), "'end_ip'");
        assertInvalid(req("10.0.0.1", "basura", "CR", null, null), "'end_ip'");
        assertInvalid(req("10.0.0.1", "2001:db8::1", "CR", null, null), "misma familia");
    }

    @Test
    @DisplayName("rechaza start_ip mayor que end_ip")
    void rejectsReversedRange() {
        assertInvalid(req("10.0.0.200", "10.0.0.1", "CR", null, null), "no puede ser mayor");
    }

    @Test
    @DisplayName("rechaza country_code inválido y coordenadas fuera de rango")
    void rejectsBadCountryAndCoordinates() {
        assertInvalid(req("10.0.0.1", "10.0.0.2", null, null, null), "'country_code'");
        assertInvalid(req("10.0.0.1", "10.0.0.2", "CRC", null, null), "'country_code'");
        assertInvalid(req("10.0.0.1", "10.0.0.2", "C1", null, null), "'country_code'");
        assertInvalid(req("10.0.0.1", "10.0.0.2", "CR", 91.0, null), "'latitude'");
        assertInvalid(req("10.0.0.1", "10.0.0.2", "CR", null, -181.0), "'longitude'");
        assertInvalid(new IpToCountryRequest("10.0.0.1", "10.0.0.2", "CR", "x".repeat(256), null, null, null), "'country_name'");
    }
}
