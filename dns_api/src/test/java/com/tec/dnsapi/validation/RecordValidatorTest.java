package com.tec.dnsapi.validation;

import com.tec.dnsapi.dto.RecordRequest;
import com.tec.dnsapi.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecordValidator")
class RecordValidatorTest {

    private static final List<Map<String, Object>> ONE_IP = List.of(Map.of("ip", "1.2.3.4", "healthy", true));

    private static RecordRequest req(String name, String type, Integer ttl, List<Map<String, Object>> ips) {
        return new RecordRequest(name, type, ttl, ips);
    }

    private static void assertInvalid(RecordRequest request, boolean requireName, String messagePart) {
        assertThatThrownBy(() -> RecordValidator.validate(request, requireName))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    @DisplayName("acepta los cinco tipos con los campos que exige el esquema")
    void acceptsAllTypes() {
        assertThatCode(() -> {
            RecordValidator.validate(req("a.example.com", "single", 300, ONE_IP), true);
            RecordValidator.validate(req("a.example.com", "multi", 0, ONE_IP), true);
            RecordValidator.validate(req("A.Example.COM.", "weight", 60,
                    List.of(Map.of("ip", "1.2.3.4", "weight", 70, "healthy", true))), true);
            RecordValidator.validate(req("a.example.com", "round-trip", 60,
                    List.of(Map.of("ip", "2001:db8::1", "latitude", 9.9, "longitude", -84.0))), true);
            RecordValidator.validate(req("*.example.com", "geo", 60,
                    List.of(Map.of("ip", "1.2.3.4", "country_code", "CR"))), true);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el nombre solo es obligatorio en POST")
    void nameOnlyRequiredOnCreate() {
        assertInvalid(req(null, "single", 60, ONE_IP), true, "'name' es obligatorio");
        assertInvalid(req("  ", "single", 60, ONE_IP), true, "'name' es obligatorio");
        assertThatCode(() -> RecordValidator.validate(req(null, "single", 60, ONE_IP), false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rechaza nombres de dominio inválidos")
    void rejectsBadNames() {
        assertInvalid(req("con espacios.com", "single", 60, ONE_IP), true, "no es un nombre de dominio válido");
        assertInvalid(req("-empieza-con-guion.com", "single", 60, ONE_IP), true, "no es un nombre de dominio válido");
        assertInvalid(req("a".repeat(64) + ".com", "single", 60, ONE_IP), true, "no es un nombre de dominio válido");
        assertInvalid(req(("a".repeat(60) + ".").repeat(5) + "com", "single", 60, ONE_IP), true, "no es un nombre de dominio válido");
    }

    @Test
    @DisplayName("rechaza tipo desconocido, ttl ausente o negativo y cuerpo nulo")
    void rejectsBadTypeTtlAndNull() {
        assertInvalid(req("a.com", "bogus", 60, ONE_IP), true, "'type'");
        assertInvalid(req("a.com", null, 60, ONE_IP), true, "'type'");
        assertInvalid(req("a.com", "single", null, ONE_IP), true, "'ttl'");
        assertInvalid(req("a.com", "single", -1, ONE_IP), true, "'ttl'");
        assertInvalid(null, true, "obligatorio");
    }

    @Test
    @DisplayName("rechaza ips vacía, nula, demasiado larga o con IP inválida")
    void rejectsBadIps() {
        assertInvalid(req("a.com", "single", 60, List.of()), true, "al menos una IP");
        assertInvalid(req("a.com", "single", 60, null), true, "al menos una IP");
        assertInvalid(req("a.com", "single", 60, List.of(Map.of("ip", "no-es-ip"))), true, "ips[0].ip");
        assertInvalid(req("a.com", "single", 60, List.of(Map.of("healthy", true))), true, "ips[0].ip");
        assertInvalid(req("a.com", "single", 60, List.of(Map.of("ip", "1.2.3.4", "healthy", "si"))), true, "healthy");

        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(Map.of("ip", "1.2.3.4"));
        }
        assertInvalid(req("a.com", "multi", 60, tooMany), true, "como máximo");
    }

    @Test
    @DisplayName("exige los campos propios de weight, round-trip y geo")
    void rejectsMissingTypeSpecificFields() {
        assertInvalid(req("a.com", "weight", 60, ONE_IP), true, "weight");
        assertInvalid(req("a.com", "weight", 60, List.of(Map.of("ip", "1.2.3.4", "weight", -5))), true, "weight");
        assertInvalid(req("a.com", "round-trip", 60, ONE_IP), true, "latitude");
        assertInvalid(req("a.com", "round-trip", 60,
                List.of(Map.of("ip", "1.2.3.4", "latitude", 95, "longitude", 0))), true, "latitude");
        assertInvalid(req("a.com", "round-trip", 60,
                List.of(Map.of("ip", "1.2.3.4", "latitude", 10, "longitude", 200))), true, "longitude");
        assertInvalid(req("a.com", "geo", 60, ONE_IP), true, "country_code");
        assertInvalid(req("a.com", "geo", 60, List.of(Map.of("ip", "1.2.3.4", "country_code", "COS"))), true, "country_code");
    }
}
