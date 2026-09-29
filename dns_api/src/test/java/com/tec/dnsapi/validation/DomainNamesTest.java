package com.tec.dnsapi.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DomainNames")
class DomainNamesTest {

    @Test
    @DisplayName("recorta, pasa a minúsculas y quita el punto final")
    void normalizes() {
        assertThat(DomainNames.normalize("  Example.COM. ")).isEqualTo("example.com");
        assertThat(DomainNames.normalize("a.b.c")).isEqualTo("a.b.c");
    }

    @Test
    @DisplayName("un nulo se convierte en cadena vacía")
    void nullBecomesEmpty() {
        assertThat(DomainNames.normalize(null)).isEmpty();
    }
}
