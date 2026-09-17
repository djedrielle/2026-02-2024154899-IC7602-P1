package com.tec.dnsapi.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xbill.DNS.Message;
import org.xbill.DNS.SimpleResolver;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;

@Component
public class DnsjavaRemoteClient implements DnsRemoteClient {

    private final SimpleResolver resolver;

    public DnsjavaRemoteClient(
            @Value("${dns.remote.host}") String host,
            @Value("${dns.remote.port}") int port,
            @Value("${dns.remote.timeout-ms}") int timeoutMs) throws UnknownHostException {

        this.resolver = new SimpleResolver(host);
        this.resolver.setPort(port);
        this.resolver.setTimeout(Duration.ofMillis(timeoutMs));
    }

    @Override
    public byte[] resolve(byte[] rawDnsPacket) throws IOException {
        Message query = new Message(rawDnsPacket);
        Message response = resolver.send(query);
        return response.toWire();
    }
}