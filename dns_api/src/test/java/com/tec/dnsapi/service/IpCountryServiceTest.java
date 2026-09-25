package com.tec.dnsapi.service;

import com.tec.dnsapi.dto.IpCountryResponse;
import com.tec.dnsapi.model.IpToCountry;
import com.tec.dnsapi.repository.IpToCountryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IpCountryService")
class IpCountryServiceTest {

    @Mock
    private IpToCountryRepository repository;

    @InjectMocks
    private IpCountryService service;

    @Nested
    @DisplayName("findCountryByIp")
    class FindCountryByIp {

        @Test
        @DisplayName("devuelve IpCountryResponse con country_code cuando la IP está en un rango")
        void returnsCountryWhenFound() {
            // IpToCountry tiene constructor protected → se mockea directamente
            IpToCountry entity = mock(IpToCountry.class);
            when(entity.getCountryCode()).thenReturn("CR");
            when(repository.findByIp("10.0.0.1")).thenReturn(Optional.of(entity));

            Object result = service.findCountryByIp("10.0.0.1");

            assertThat(result).isInstanceOf(IpCountryResponse.class);
            assertThat(((IpCountryResponse) result).country_code()).isEqualTo("CR");
        }

        @Test
        @DisplayName("devuelve false cuando la IP no está en ningún rango")
        void returnsFalseWhenNotFound() {
            when(repository.findByIp("192.168.1.1")).thenReturn(Optional.empty());

            Object result = service.findCountryByIp("192.168.1.1");

            assertThat(result).isEqualTo(false);
        }

        @Test
        @DisplayName("devuelve false cuando ip es null")
        void returnsFalseForNull() {
            Object result = service.findCountryByIp(null);

            assertThat(result).isEqualTo(false);
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("devuelve false cuando ip está en blanco")
        void returnsFalseForBlank() {
            Object result = service.findCountryByIp("   ");

            assertThat(result).isEqualTo(false);
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("hace trim de la IP antes de buscar")
        void trimsIp() {
            IpToCountry entity = mock(IpToCountry.class);
            when(entity.getCountryCode()).thenReturn("US");
            when(repository.findByIp("8.8.8.8")).thenReturn(Optional.of(entity));

            service.findCountryByIp("  8.8.8.8  ");

            verify(repository).findByIp("8.8.8.8");
        }
    }
}
