package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.RecordRequest;
import com.tec.dnsapi.dto.RecordResponse;
import com.tec.dnsapi.model.DnsRecord;
import com.tec.dnsapi.repository.DnsRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DnsRecordService")
class DnsRecordServiceTest {

    @Mock
    private DnsRecordRepository repository;

    @InjectMocks
    private DnsRecordService service;

    private DnsRecord sampleRecord;
    private List<Map<String, Object>> sampleIps;

    @BeforeEach
    void setUp() {
        sampleIps = List.of(Map.of("ip", "1.2.3.4", "healthy", true));
        sampleRecord = new DnsRecord("example.com", "single", 300, sampleIps);
    }

    // -------------------------------------------------------------------------
    // findByDomain
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findByDomain")
    class FindByDomain {

        @Test
        @DisplayName("devuelve RecordResponse cuando el dominio existe")
        void returnsRecordWhenFound() {
            when(repository.findById("example.com")).thenReturn(Optional.of(sampleRecord));

            Object result = service.findByDomain("example.com");

            assertThat(result).isInstanceOf(RecordResponse.class);
            RecordResponse response = (RecordResponse) result;
            assertThat(response.name()).isEqualTo("example.com");
            assertThat(response.type()).isEqualTo("single");
            assertThat(response.ttl()).isEqualTo(300);
        }

        @Test
        @DisplayName("devuelve false cuando el dominio no existe")
        void returnsFalseWhenNotFound() {
            when(repository.findById("notfound.com")).thenReturn(Optional.empty());

            Object result = service.findByDomain("notfound.com");

            assertThat(result).isEqualTo(false);
        }

        @Test
        @DisplayName("normaliza el dominio: trim + lowercase + quita punto final")
        void normalizesDomain() {
            when(repository.findById("example.com")).thenReturn(Optional.of(sampleRecord));

            service.findByDomain("  EXAMPLE.COM.  ");

            verify(repository).findById("example.com");
        }

        @Test
        @DisplayName("normaliza dominio en mayúsculas")
        void normalizesUppercase() {
            when(repository.findById("example.com")).thenReturn(Optional.of(sampleRecord));

            service.findByDomain("EXAMPLE.COM");

            verify(repository).findById("example.com");
        }

        @Test
        @DisplayName("normaliza dominio con punto final (FQDN)")
        void normalizesTrailingDot() {
            when(repository.findById("example.com")).thenReturn(Optional.of(sampleRecord));

            service.findByDomain("example.com.");

            verify(repository).findById("example.com");
        }
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("devuelve lista vacía cuando no hay registros")
        void returnsEmptyList() {
            when(repository.findAll()).thenReturn(List.of());

            List<RecordResponse> result = service.findAll();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("devuelve todos los registros mapeados a RecordResponse")
        void returnsMappedRecords() {
            DnsRecord second = new DnsRecord("other.com", "multi", 60,
                    List.of(Map.of("ip", "2.3.4.5", "healthy", true)));
            when(repository.findAll()).thenReturn(List.of(sampleRecord, second));

            List<RecordResponse> result = service.findAll();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("example.com");
            assertThat(result.get(1).name()).isEqualTo("other.com");
        }
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("crea y devuelve el registro cuando el nombre no existe")
        void createsRecord() {
            RecordRequest req = new RecordRequest("newdomain.com", "single", 300, sampleIps);
            DnsRecord saved = new DnsRecord("newdomain.com", "single", 300, sampleIps);

            when(repository.existsById("newdomain.com")).thenReturn(false);
            when(repository.save(any(DnsRecord.class))).thenReturn(saved);

            RecordResponse result = service.create(req);

            assertThat(result.name()).isEqualTo("newdomain.com");
            assertThat(result.type()).isEqualTo("single");
            verify(repository).save(any(DnsRecord.class));
        }

        @Test
        @DisplayName("normaliza el nombre antes de crear")
        void normalizesNameOnCreate() {
            RecordRequest req = new RecordRequest("NEW.COM.", "single", 300, sampleIps);
            DnsRecord saved = new DnsRecord("new.com", "single", 300, sampleIps);

            when(repository.existsById("new.com")).thenReturn(false);
            when(repository.save(any(DnsRecord.class))).thenReturn(saved);

            RecordResponse result = service.create(req);

            assertThat(result.name()).isEqualTo("new.com");
        }

        @Test
        @DisplayName("lanza 409 Conflict cuando el dominio ya existe")
        void throwsConflictWhenDomainExists() {
            RecordRequest req = new RecordRequest("example.com", "single", 300, sampleIps);
            when(repository.existsById("example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT));

            verify(repository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("actualiza y devuelve el registro modificado")
        void updatesRecord() {
            List<Map<String, Object>> newIps = List.of(Map.of("ip", "9.9.9.9", "healthy", true));
            RecordRequest req = new RecordRequest("example.com", "multi", 600, newIps);

            when(repository.findById("example.com")).thenReturn(Optional.of(sampleRecord));
            when(repository.save(sampleRecord)).thenReturn(sampleRecord);

            RecordResponse result = service.update("example.com", req);

            assertThat(result.type()).isEqualTo("multi");
            assertThat(result.ttl()).isEqualTo(600);
            verify(repository).save(sampleRecord);
        }

        @Test
        @DisplayName("normaliza el nombre para buscar el registro a editar")
        void normalizesNameOnUpdate() {
            RecordRequest req = new RecordRequest("example.com", "single", 300, sampleIps);
            when(repository.findById("example.com")).thenReturn(Optional.of(sampleRecord));
            when(repository.save(any())).thenReturn(sampleRecord);

            service.update("EXAMPLE.COM.", req);

            verify(repository).findById("example.com");
        }

        @Test
        @DisplayName("lanza 404 Not Found cuando el dominio no existe")
        void throwsNotFoundWhenMissing() {
            RecordRequest req = new RecordRequest("ghost.com", "single", 300, sampleIps);
            when(repository.findById("ghost.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update("ghost.com", req))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));

            verify(repository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("elimina el registro cuando existe")
        void deletesRecord() {
            when(repository.existsById("example.com")).thenReturn(true);

            service.delete("example.com");

            verify(repository).deleteById("example.com");
        }

        @Test
        @DisplayName("normaliza el nombre antes de eliminar")
        void normalizesNameOnDelete() {
            when(repository.existsById("example.com")).thenReturn(true);

            service.delete("EXAMPLE.COM.");

            verify(repository).deleteById("example.com");
        }

        @Test
        @DisplayName("lanza 404 Not Found cuando el dominio no existe")
        void throwsNotFoundWhenMissing() {
            when(repository.existsById("ghost.com")).thenReturn(false);

            assertThatThrownBy(() -> service.delete("ghost.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));

            verify(repository, never()).deleteById(any());
        }
    }
}
