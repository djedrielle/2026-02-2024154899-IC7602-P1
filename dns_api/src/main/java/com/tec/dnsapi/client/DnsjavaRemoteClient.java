package com.tec.dnsapi.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xbill.DNS.Message;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;

@Component
public class DnsjavaRemoteClient implements DnsRemoteClient {

    private final Resolver resolver;
    private final int retries;

    @Autowired
    public DnsjavaRemoteClient(
            @Value("${dns.remote.host}") String host,
            @Value("${dns.remote.port}") int port,
            @Value("${dns.remote.timeout-ms}") int timeoutMs,
            @Value("${dns.remote.retries}") int retries) throws UnknownHostException {
        this(buildResolver(host, port, timeoutMs), retries);
    }

    DnsjavaRemoteClient(Resolver resolver, int retries) {
        this.resolver = resolver;
        this.retries = Math.max(0, retries);
    }

    private static Resolver buildResolver(String host, int port, int timeoutMs) throws UnknownHostException {
        SimpleResolver simple = new SimpleResolver(host);
        simple.setPort(port);
        simple.setTimeout(Duration.ofMillis(timeoutMs));
        return simple;
    }

    /** Un solo paquete UDP perdido no debe fallar la resolución: se reintenta 'retries' veces más. */
    @Override
    public byte[] resolve(byte[] rawDnsPacket) throws IOException {
        Message query = new Message(rawDnsPacket);
        IOException lastError = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return resolver.send(query).toWire();
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError;
    }
}
