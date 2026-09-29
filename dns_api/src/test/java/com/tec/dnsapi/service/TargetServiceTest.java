package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.TargetRequest;
import com.tec.dnsapi.dto.TargetResponse;
import com.tec.dnsapi.exception.InvalidRequestException;
import com.tec.dnsapi.model.Target;
import com.tec.dnsapi.repository.DnsRecordRepository;
import com.tec.dnsapi.repository.TargetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TargetService")
class TargetServiceTest {

    @Mock
    private TargetRepository targetRepository;

    @Mock
    private DnsRecordRepository recordRepository;

    @InjectMocks
    private TargetService service;

    private static TargetRequest httpRequest(String recordName) {
        return new TargetRequest(recordName, " 10.0.0.1 ", 8080, "http", 1500, 3, "/health",
                List.of(200, 204), "user", "pass");
    }

    private static Target existingTarget() {
        return new Target("a.example.com", "10.0.0.9", 80, "TCP", 1000, 1, null, null, null, null);
    }

    @Test
    @DisplayName("create normaliza nombre y tipo, y guarda el target")
    void createNormalizesAndSaves() {
        when(recordRepository.existsById("a.example.com")).thenReturn(true);
        when(targetRepository.save(any(Target.class))).thenAnswer(inv -> inv.getArgument(0));

        TargetResponse response = service.create(httpRequest("A.Example.COM."));

        ArgumentCaptor<Target> captor = ArgumentCaptor.forClass(Target.class);
        verify(targetRepository).save(captor.capture());
        Target saved = captor.getValue();
        assertThat(saved.getRecordName()).isEqualTo("a.example.com");
        assertThat(saved.getCheckType()).isEqualTo("HTTP");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(response.expected_status_codes()).containsExactly(200, 204);
        assertThat(response.basic_auth_user()).isEqualTo("user");
    }

    @Test
    @DisplayName("create rechaza con InvalidRequestException un registro DNS que no existe")
    void createRejectsUnknownRecord() {
        when(recordRepository.existsById("ghost.com")).thenReturn(false);

        assertThatThrownBy(() -> service.create(httpRequest("ghost.com")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("ghost.com");
        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rechaza datos inválidos antes de tocar la base")
    void createRejectsInvalidData() {
        TargetRequest bad = new TargetRequest("a.com", "10.0.0.1", 99999, "TCP", 1000, 1, null, null, null, null);

        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(recordRepository, targetRepository);
    }

    @Test
    @DisplayName("update modifica el target existente")
    void updateModifiesTarget() {
        UUID id = UUID.randomUUID();
        Target existing = existingTarget();
        when(targetRepository.findById(id)).thenReturn(java.util.Optional.of(existing));
        when(recordRepository.existsById("a.example.com")).thenReturn(true);
        when(targetRepository.save(existing)).thenReturn(existing);

        TargetResponse response = service.update(id, httpRequest("a.example.com"));

        assertThat(existing.getCheckType()).isEqualTo("HTTP");
        assertThat(existing.getPort()).isEqualTo(8080);
        assertThat(response.http_path()).isEqualTo("/health");
    }

    @Test
    @DisplayName("update responde 404 si el target no existe")
    void updateNotFound() {
        UUID id = UUID.randomUUID();
        when(targetRepository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.update(id, httpRequest("a.example.com")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("delete elimina por id y responde 404 si no existe")
    void deleteById() {
        UUID id = UUID.randomUUID();
        when(targetRepository.existsById(id)).thenReturn(true, false);

        service.delete(id);
        verify(targetRepository).deleteById(id);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("deleteByRecordName delega en el repositorio")
    void deleteByRecordName() {
        service.deleteByRecordName("a.example.com");

        verify(targetRepository).deleteByRecordName("a.example.com");
    }

    @Test
    @DisplayName("findAll y findByRecordName devuelven los targets mapeados")
    void findMapsResponses() {
        Target target = existingTarget();
        when(targetRepository.findAll()).thenReturn(List.of(target));
        when(targetRepository.findByRecordName("a.example.com")).thenReturn(List.of(target));

        assertThat(service.findAll()).hasSize(1);
        assertThat(service.findByRecordName("a.example.com")).singleElement()
                .satisfies(r -> assertThat(r.ip_address()).isEqualTo("10.0.0.9"));
    }
}
