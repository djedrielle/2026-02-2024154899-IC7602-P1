package com.tec.dnsapi.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpAddresses")
class IpAddressesTest {

    @Test
    @DisplayName("acepta IPv4 e IPv6 literales, con espacios alrededor")
    void acceptsLiterals() {
        assertThat(IpAddresses.isValid("8.8.8.8")).isTrue();
        assertThat(IpAddresses.isValid(" 10.0.0.1 ")).isTrue();
        assertThat(IpAddresses.isValid("2001:4860:4860::8888")).isTrue();
        assertThat(IpAddresses.isValid("::1")).isTrue();
    }

    @Test
    @DisplayName("rechaza textos que no son una IP literal")
    void rejectsGarbage() {
        assertThat(IpAddresses.isValid(null)).isFalse();
        assertThat(IpAddresses.isValid("")).isFalse();
        assertThat(IpAddresses.isValid("no-es-ip")).isFalse();
        assertThat(IpAddresses.isValid("256.1.1.1")).isFalse();
        assertThat(IpAddresses.isValid("1.2.3")).isFalse();
        assertThat(IpAddresses.isValid("example.com")).isFalse();
    }

    @Test
    @DisplayName("compara bytes como enteros sin signo")
    void comparesUnsigned() {
        byte[] low = IpAddresses.parse("10.0.0.1");
        byte[] high = IpAddresses.parse("200.0.0.1");

        assertThat(IpAddresses.compare(low, high)).isNegative();
        assertThat(IpAddresses.compare(high, low)).isPositive();
        assertThat(IpAddresses.compare(low, IpAddresses.parse("10.0.0.1"))).isZero();
    }
}
