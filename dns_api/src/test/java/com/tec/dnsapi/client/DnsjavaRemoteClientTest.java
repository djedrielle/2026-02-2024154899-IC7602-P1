package com.tec.dnsapi.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DnsjavaRemoteClient")
class DnsjavaRemoteClientTest {

    private static byte[] queryWire() throws Exception {
        return Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN)).toWire();
    }

    /** Resolver falso: falla las primeras 'failures' veces y luego responde con la misma consulta. */
    private static Resolver flaky(int failures, AtomicInteger calls) throws IOException {
        Resolver resolver = mock(Resolver.class);
        when(resolver.send(any(Message.class))).thenAnswer(inv -> {
            if (calls.incrementAndGet() <= failures) {
                throw new IOException("timeout simulado");
            }
            return inv.getArgument(0);
        });
        return resolver;
    }

    @Test
    @DisplayName("devuelve la respuesta a la primera si el upstream contesta")
    void succeedsFirstTry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        byte[] wire = queryWire();

        byte[] response = new DnsjavaRemoteClient(flaky(0, calls), 2).resolve(wire);

        assertThat(calls).hasValue(1);
        assertThat(new Message(response).getQuestion().getName().toString()).isEqualTo("example.com.");
    }

    @Test
    @DisplayName("reintenta tras un paquete perdido y termina bien")
    void retriesAfterLoss() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        byte[] response = new DnsjavaRemoteClient(flaky(1, calls), 1).resolve(queryWire());

        assertThat(response).isNotEmpty();
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("agotados los reintentos propaga el IOException")
    void failsAfterRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DnsjavaRemoteClient client = new DnsjavaRemoteClient(flaky(10, calls), 2);
        byte[] wire = queryWire();

        assertThatThrownBy(() -> client.resolve(wire)).isInstanceOf(IOException.class).hasMessageContaining("timeout");
        assertThat(calls).hasValue(3);
    }

    @Test
    @DisplayName("un valor de reintentos negativo equivale a un solo intento")
    void negativeRetriesMeansSingleAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DnsjavaRemoteClient client = new DnsjavaRemoteClient(flaky(10, calls), -3);
        byte[] wire = queryWire();

        assertThatThrownBy(() -> client.resolve(wire)).isInstanceOf(IOException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("el constructor de producción arma el resolver con host, puerto y timeout configurados")
    void productionConstructorBuildsResolver() throws Exception {
        DnsjavaRemoteClient client = new DnsjavaRemoteClient("127.0.0.1", 5353, 200, 0);
        byte[] wire = queryWire();

        assertThatThrownBy(() -> client.resolve(wire)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("un paquete que no es DNS falla sin llegar al upstream")
    void malformedPacketFails() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DnsjavaRemoteClient client = new DnsjavaRemoteClient(flaky(0, calls), 2);

        assertThatThrownBy(() -> client.resolve(new byte[] { 1, 2, 3 })).isInstanceOf(IOException.class);
        assertThat(calls).hasValue(0);
    }
}
