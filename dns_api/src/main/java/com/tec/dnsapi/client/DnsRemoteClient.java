package com.tec.dnsapi.client;

import java.io.IOException;

public interface DnsRemoteClient {
    byte[] resolve(byte[] rawDnsPacket) throws IOException;
}

