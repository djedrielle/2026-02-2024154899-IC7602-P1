package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.RecordRequest;
import com.tec.dnsapi.exception.InvalidRequestException;
import com.tec.dnsapi.repository.DnsRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DnsRecordService: validación de entrada")
class DnsRecordServiceValidationTest {

    @Mock
    private DnsRecordRepository repository;

    @InjectMocks
    private DnsRecordService service;

    private static final List<Map<String, Object>> IPS = List.of(Map.of("ip", "1.2.3.4", "healthy", true));

    @Test
    @DisplayName("create rechaza nombre vacío, tipo inválido, ips vacía y ttl negativo sin escribir")
    void createRejectsInvalidData() {
        assertThatThrownBy(() -> service.create(new RecordRequest("", "single", 60, IPS)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create(new RecordRequest("a.com", "bogus", 60, IPS)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create(new RecordRequest("a.com", "single", 60, List.of())))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create(new RecordRequest("a.com", "single", -5, IPS)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("update valida el body pero no exige name (viene en la ruta)")
    void updateValidatesBody() {
        assertThatThrownBy(() -> service.update("a.com", new RecordRequest(null, "bogus", 60, IPS)))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).save(any());
    }
}
