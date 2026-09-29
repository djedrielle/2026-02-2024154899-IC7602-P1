package com.tec.dnsapi.validation;

import com.tec.dnsapi.dto.TargetRequest;
import com.tec.dnsapi.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TargetValidator")
class TargetValidatorTest {

    private static TargetRequest tcp() {
        return new TargetRequest("a.example.com", "10.0.0.1", 80, "TCP", 1000, 2, null, null, null, null);
    }

    private static TargetRequest http(String path, List<Integer> codes) {
        return new TargetRequest("a.example.com", "10.0.0.1", 8080, "http", 1500, 3, path, codes, "u", "p");
    }

    private static void assertInvalid(TargetRequest request, String messagePart) {
        assertThatThrownBy(() -> TargetValidator.validate(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    @DisplayName("acepta TCP y HTTP y devuelve el check_type en mayúsculas")
    void acceptsAndNormalizesType() {
        assertThat(TargetValidator.validate(tcp())).isEqualTo("TCP");
        assertThat(TargetValidator.validate(http("/health", List.of(200, 204)))).isEqualTo("HTTP");
        assertThat(TargetValidator.validate(http("/", null))).isEqualTo("HTTP");
    }

    @Test
    @DisplayName("rechaza campos obligatorios ausentes o inválidos")
    void rejectsBadCommonFields() {
        assertInvalid(null, "obligatorio");
        assertInvalid(new TargetRequest(null, "10.0.0.1", 80, "TCP", 1000, 1, null, null, null, null), "'record_name'");
        assertInvalid(new TargetRequest(" ", "10.0.0.1", 80, "TCP", 1000, 1, null, null, null, null), "'record_name'");
        assertInvalid(new TargetRequest("a.com", "basura", 80, "TCP", 1000, 1, null, null, null, null), "'ip_address'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", 0, "TCP", 1000, 1, null, null, null, null), "'port'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", 99999, "TCP", 1000, 1, null, null, null, null), "'port'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", null, "TCP", 1000, 1, null, null, null, null), "'port'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", 80, "UDP", 1000, 1, null, null, null, null), "'check_type'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", 80, null, 1000, 1, null, null, null, null), "'check_type'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", 80, "TCP", 0, 1, null, null, null, null), "'timeout_ms'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", 80, "TCP", null, 1, null, null, null, null), "'timeout_ms'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", 80, "TCP", 1000, -1, null, null, null, null), "'retries'");
        assertInvalid(new TargetRequest("a.com", "10.0.0.1", 80, "TCP", 1000, null, null, null, null, null), "'retries'");
    }

    @Test
    @DisplayName("un target HTTP exige http_path que empiece con '/' y códigos entre 100 y 599")
    void rejectsBadHttpFields() {
        assertInvalid(http(null, null), "'http_path'");
        assertInvalid(http("health", null), "'http_path'");
        assertInvalid(http("/", List.of(99)), "'expected_status_codes'");
        assertInvalid(http("/", List.of(600)), "'expected_status_codes'");
        assertInvalid(http("/", Arrays.asList(200, null)), "'expected_status_codes'");
    }
}
