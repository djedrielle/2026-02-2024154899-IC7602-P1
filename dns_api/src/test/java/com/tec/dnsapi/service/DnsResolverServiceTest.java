package com.tec.dnsapi.service;

import com.tec.dnsapi.client.DnsRemoteClient;
import com.tec.dnsapi.dto.DnsResolverRequest;
import com.tec.dnsapi.dto.DnsResolverResponse;
import com.tec.dnsapi.exception.DnsResolutionException;
import com.tec.dnsapi.exception.InvalidDnsPacketException;
import com.tec.dnsapi.model.DnsRecord;
import com.tec.dnsapi.repository.DnsRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DnsResolverService")
class DnsResolverServiceTest {

    @Mock
    private DnsRemoteClient remoteClient;

    @Mock
    private DnsRecordRepository recordRepository;

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

        @Test
        @DisplayName("guarda en BD con tipo single cuando la respuesta tiene una sola IP")
        void savesSingleRecordInDatabase() throws Exception {
            byte[] rawQuery = new byte[] { 0x01 };

            Message response = new Message();
            response.getHeader().setRcode(Rcode.NOERROR);
            Name domain = Name.fromString("test.example.com.");
            response.addRecord(Record.newRecord(domain, Type.A, DClass.IN), Section.QUESTION);
            response.addRecord(new ARecord(domain, DClass.IN, 120, InetAddress.getByName("192.168.1.10")),
                    Section.ANSWER);

            byte[] rawResponse = response.toWire();
            when(remoteClient.resolve(rawQuery)).thenReturn(rawResponse);
            when(recordRepository.findById("test.example.com")).thenReturn(Optional.empty());

            service.resolveDecoded(rawQuery);

            ArgumentCaptor<DnsRecord> captor = ArgumentCaptor.forClass(DnsRecord.class);
            verify(recordRepository).save(captor.capture());

            DnsRecord saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("test.example.com");
            assertThat(saved.getType()).isEqualTo("single");
            assertThat(saved.getTtl()).isEqualTo(120);
            assertThat(saved.getIps()).hasSize(1);
            assertThat(saved.getIps().get(0)).containsEntry("ip", "192.168.1.10");
            assertThat(saved.getIps().get(0)).containsEntry("healthy", true);
        }

        @Test
        @DisplayName("guarda en BD con tipo multi cuando la respuesta tiene múltiples IPs")
        void savesMultiRecordInDatabase() throws Exception {
            byte[] rawQuery = new byte[] { 0x01 };

            Message response = new Message();
            response.getHeader().setRcode(Rcode.NOERROR);
            Name domain = Name.fromString("multi.example.com.");
            response.addRecord(Record.newRecord(domain, Type.A, DClass.IN), Section.QUESTION);
            response.addRecord(new ARecord(domain, DClass.IN, 300, InetAddress.getByName("10.0.0.1")), Section.ANSWER);
            response.addRecord(new ARecord(domain, DClass.IN, 300, InetAddress.getByName("10.0.0.2")), Section.ANSWER);

            byte[] rawResponse = response.toWire();
            when(remoteClient.resolve(rawQuery)).thenReturn(rawResponse);
            when(recordRepository.findById("multi.example.com")).thenReturn(Optional.empty());

            service.resolveDecoded(rawQuery);

            ArgumentCaptor<DnsRecord> captor = ArgumentCaptor.forClass(DnsRecord.class);
            verify(recordRepository).save(captor.capture());

            DnsRecord saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("multi.example.com");
            assertThat(saved.getType()).isEqualTo("multi");
            assertThat(saved.getIps()).hasSize(2);
            assertThat(saved.getCounter()).isEqualTo(0);
        }

        @Test
        @DisplayName("actualiza el registro si ya existía en la BD")
        void updatesExistingRecordInDatabase() throws Exception {
            byte[] rawQuery = new byte[] { 0x01 };

            Message response = new Message();
            response.getHeader().setRcode(Rcode.NOERROR);
            Name domain = Name.fromString("existing.com.");
            response.addRecord(Record.newRecord(domain, Type.A, DClass.IN), Section.QUESTION);
            response.addRecord(new ARecord(domain, DClass.IN, 60, InetAddress.getByName("1.1.1.1")), Section.ANSWER);

            byte[] rawResponse = response.toWire();
            when(remoteClient.resolve(rawQuery)).thenReturn(rawResponse);

            DnsRecord existing = new DnsRecord("existing.com", "single", 300, java.util.List.of());
            when(recordRepository.findById("existing.com")).thenReturn(Optional.of(existing));

            service.resolveDecoded(rawQuery);

            verify(recordRepository).save(existing);
            assertThat(existing.getTtl()).isEqualTo(60);
            assertThat(existing.getIps()).hasSize(1);
            assertThat(existing.getIps().get(0)).containsEntry("ip", "1.1.1.1");
        }

        @Test
        @DisplayName("la resolución no falla aunque ocurra un error al guardar en la BD")
        void succeedsEvenIfDatabaseFails() throws Exception {
            byte[] rawQuery = new byte[] { 0x01 };

            Message response = new Message();
            response.getHeader().setRcode(Rcode.NOERROR);
            Name domain = Name.fromString("faildb.com.");
            response.addRecord(Record.newRecord(domain, Type.A, DClass.IN), Section.QUESTION);
            response.addRecord(new ARecord(domain, DClass.IN, 60, InetAddress.getByName("2.2.2.2")), Section.ANSWER);

            byte[] rawResponse = response.toWire();
            when(remoteClient.resolve(rawQuery)).thenReturn(rawResponse);
            when(recordRepository.findById("faildb.com")).thenThrow(new RuntimeException("DB connection error"));

            DnsResolverResponse result = service.resolveDecoded(rawQuery);

            assertThat(result).isNotNull();
            assertThat(result.data()).isEqualTo(Base64.getEncoder().encodeToString(rawResponse));
        }
    }
}
