package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.IpToCountryFullResponse;
import com.tec.dnsapi.dto.IpToCountryRequest;
import com.tec.dnsapi.exception.InvalidRequestException;
import com.tec.dnsapi.model.IpToCountry;
import com.tec.dnsapi.repository.IpToCountryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IpCountryService: validación y paginación")
class IpCountryServiceValidationTest {

    @Mock
    private IpToCountryRepository repository;

    @InjectMocks
    private IpCountryService service;

    private static IpToCountryRequest range(String start, String end, String code) {
        return new IpToCountryRequest(start, end, code, "Costa Rica", "San José", 9.9, -84.0);
    }

    @Test
    @DisplayName("findCountryByIp rechaza IPs inválidas con InvalidRequestException")
    void lookupRejectsInvalidIp() {
        assertThatThrownBy(() -> service.findCountryByIp("no-es-ip")).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("findCountryByIp devuelve false para IP vacía sin consultar la base")
    void lookupBlankIp() {
        assertThat(service.findCountryByIp("  ")).isEqualTo(false);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("findPage pide la página ordenada por id y mapea los resultados")
    void findPageBuildsPageRequest() {
        IpToCountry entity = new IpToCountry("10.0.0.0", "10.0.0.255", "CR", "Costa Rica", null, null, null);
        when(repository.findAllBy(any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(entity)));

        List<IpToCountryFullResponse> result = service.findPage(2, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllBy(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort().getOrderFor("id")).isNotNull();
        assertThat(result).singleElement().satisfies(r -> assertThat(r.country_code()).isEqualTo("CR"));
    }

    @Test
    @DisplayName("findPage rechaza página negativa y tamaños fuera de 1..1000")
    void findPageRejectsBadParams() {
        assertThatThrownBy(() -> service.findPage(-1, 10)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.findPage(0, 0)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.findPage(0, 1001)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("create guarda el country_code en mayúsculas y sin espacios en las IPs")
    void createNormalizes() {
        when(repository.save(any(IpToCountry.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(range(" 10.0.0.1 ", "10.0.0.9", "cr"));

        ArgumentCaptor<IpToCountry> captor = ArgumentCaptor.forClass(IpToCountry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCountryCode()).isEqualTo("CR");
        assertThat(captor.getValue().getStartIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("create y update rechazan un rango invertido sin escribir")
    void writesRejectInvalidRange() {
        assertThatThrownBy(() -> service.create(range("10.0.0.9", "10.0.0.1", "CR")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.update(1L, range("basura", "10.0.0.1", "CR")))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update normaliza y guarda el rango existente")
    void updateNormalizes() {
        IpToCountry entity = new IpToCountry("1.1.1.1", "1.1.1.2", "US", null, null, null, null);
        when(repository.findById(3L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        service.update(3L, range("10.0.0.1", "10.0.0.9", "cr"));

        assertThat(entity.getCountryCode()).isEqualTo("CR");
        assertThat(entity.getEndIp()).isEqualTo("10.0.0.9");
    }
}
