package com.tec.dnsapi.service;

import com.tec.dnsapi.client.DnsRemoteClient;
import com.tec.dnsapi.dto.DnsResolverRequest;
import com.tec.dnsapi.dto.DnsResolverResponse;
import com.tec.dnsapi.exception.DnsResolutionException;
import com.tec.dnsapi.exception.InvalidDnsPacketException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DnsResolverService")
class DnsResolverServiceTest {

    @Mock
    private DnsRemoteClient remoteClient;

    @InjectMocks
    private DnsResolverService service;

    // -------------------------------------------------------------------------
    // decodeAndValidate
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("decodeAndValidate")
    class DecodeAndValidate {

        @Test
        @DisplayName("decodifica correctamente un BASE64 válido")
        void decodesValidBase64() {
            byte[] original = new byte[] { 0x01, 0x02, 0x03 };
            String encoded = Base64.getEncoder().encodeToString(original);
            DnsResolverRequest request = new DnsResolverRequest(encoded);

            byte[] result = service.decodeAndValidate(request);

            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("lanza InvalidDnsPacketException cuando request es null")
        void throwsWhenRequestIsNull() {
            assertThatThrownBy(() -> service.decodeAndValidate(null))
                    .isInstanceOf(InvalidDnsPacketException.class)
                    .hasMessageContaining("obligatorio");
        }

        @Test
        @DisplayName("lanza InvalidDnsPacketException cuando data es null")
        void throwsWhenDataIsNull() {
            DnsResolverRequest request = new DnsResolverRequest(null);

            assertThatThrownBy(() -> service.decodeAndValidate(request))
                    .isInstanceOf(InvalidDnsPacketException.class)
                    .hasMessageContaining("obligatorio");
        }

        @Test
        @DisplayName("lanza InvalidDnsPacketException cuando data está vacío")
        void throwsWhenDataIsBlank() {
            DnsResolverRequest request = new DnsResolverRequest("   ");

            assertThatThrownBy(() -> service.decodeAndValidate(request))
                    .isInstanceOf(InvalidDnsPacketException.class)
                    .hasMessageContaining("obligatorio");
        }

        @Test
        @DisplayName("lanza InvalidDnsPacketException cuando data no es BASE64 válido")
        void throwsWhenDataIsNotBase64() {
            DnsResolverRequest request = new DnsResolverRequest("!!!no-es-base64!!!");

            assertThatThrownBy(() -> service.decodeAndValidate(request))
                    .isInstanceOf(InvalidDnsPacketException.class)
                    .hasMessageContaining("BASE64");
        }
    }

    // -------------------------------------------------------------------------
    // resolveDecoded
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("resolveDecoded")
    class ResolveDecoded {

        @Test
        @DisplayName("devuelve la respuesta en BASE64 cuando el cliente UDP responde")
        void returnsBase64EncodedResponse() throws IOException {
            byte[] rawQuery = new byte[] { 0x10, 0x20 };
            byte[] rawResponse = new byte[] { 0x30, 0x40, 0x50 };

            when(remoteClient.resolve(rawQuery)).thenReturn(rawResponse);

            DnsResolverResponse result = service.resolveDecoded(rawQuery);

            String expectedBase64 = Base64.getEncoder().encodeToString(rawResponse);
            assertThat(result.data()).isEqualTo(expectedBase64);
        }

        @Test
        @DisplayName("lanza DnsResolutionException cuando el cliente UDP falla con IOException")
        void throwsWhenClientFails() throws IOException {
            byte[] rawQuery = new byte[] { 0x10, 0x20 };

            when(remoteClient.resolve(rawQuery))
                    .thenThrow(new IOException("timeout"));

            assertThatThrownBy(() -> service.resolveDecoded(rawQuery))
                    .isInstanceOf(DnsResolutionException.class)
                    .hasMessageContaining("DNS remoto");
        }

        @Test
        @DisplayName("el BASE64 resultante es decodificable y coincide con la respuesta raw")
        void base64IsDecodable() throws IOException {
            byte[] rawQuery = new byte[] { 0x01 };
            byte[] rawResponse = "respuesta-dns".getBytes();

            when(remoteClient.resolve(rawQuery)).thenReturn(rawResponse);

            DnsResolverResponse result = service.resolveDecoded(rawQuery);
            byte[] decoded = Base64.getDecoder().decode(result.data());

            assertThat(decoded).isEqualTo(rawResponse);
        }
    }
}
