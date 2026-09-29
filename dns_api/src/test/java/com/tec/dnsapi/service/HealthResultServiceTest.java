package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.HealthResultResponse;
import com.tec.dnsapi.model.HealthResult;
import com.tec.dnsapi.repository.HealthResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthResultService")
class HealthResultServiceTest {

    @Mock
    private HealthResultRepository repository;

    @InjectMocks
    private HealthResultService service;

    private static HealthResult sample() {
        HealthResult r = mock(HealthResult.class);
        when(r.getId()).thenReturn(5L);
        when(r.getRecordName()).thenReturn("a.example.com");
        when(r.getIpAddress()).thenReturn("10.0.0.1");
        when(r.getIsHealthy()).thenReturn(true);
        when(r.getLatencyMs()).thenReturn(12.5);
        when(r.getCheckerLocationId()).thenReturn("CR-01");
        when(r.getCheckedAt()).thenReturn(Instant.parse("2026-09-29T00:00:00Z"));
        return r;
    }

    @Test
    @DisplayName("findAll mapea todos los resultados, incluida la fecha en ISO-8601")
    void findAll() {
        HealthResult row = sample();
        when(repository.findAll()).thenReturn(List.of(row));

        List<HealthResultResponse> result = service.findAll();

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(5L);
            assertThat(r.checker_location_id()).isEqualTo("CR-01");
            assertThat(r.latency_ms()).isEqualTo(12.5);
            assertThat(r.checked_at()).isEqualTo("2026-09-29T00:00:00Z");
        });
    }

    @Test
    @DisplayName("una fecha ausente se devuelve como null")
    void nullCheckedAt() {
        HealthResult r = mock(HealthResult.class);
        when(repository.findAll()).thenReturn(List.of(r));

        assertThat(service.findAll()).singleElement().satisfies(x -> assertThat(x.checked_at()).isNull());
    }

    @Test
    @DisplayName("filtra por record_name y por target_id")
    void filters() {
        UUID targetId = UUID.randomUUID();
        HealthResult first = sample();
        HealthResult second = sample();
        HealthResult third = sample();
        when(repository.findByRecordNameOrderByCheckedAtDesc("a.example.com")).thenReturn(List.of(first));
        when(repository.findByTargetIdOrderByCheckedAtDesc(targetId)).thenReturn(List.of(second, third));

        assertThat(service.findByRecordName("a.example.com")).hasSize(1);
        assertThat(service.findByTargetId(targetId)).hasSize(2);
    }

    @Test
    @DisplayName("delete elimina por id y responde 404 si no existe")
    void delete() {
        when(repository.existsById(5L)).thenReturn(true);
        when(repository.existsById(99L)).thenReturn(false);

        service.delete(5L);
        verify(repository).deleteById(5L);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("deleteByRecordName delega en el repositorio")
    void deleteByRecordName() {
        service.deleteByRecordName("a.example.com");

        verify(repository).deleteByRecordName("a.example.com");
    }
}
