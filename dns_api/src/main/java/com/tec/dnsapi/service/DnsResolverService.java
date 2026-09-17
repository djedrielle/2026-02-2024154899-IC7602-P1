package com.tec.dnsapi.service;

import com.tec.dnsapi.client.DnsRemoteClient;
import com.tec.dnsapi.dto.DnsResolverRequest;
import com.tec.dnsapi.dto.DnsResolverResponse;
import com.tec.dnsapi.exception.DnsResolutionException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;

@Service
public class DnsResolverService {

    private final DnsRemoteClient remoteClient;

    public DnsResolverService(DnsRemoteClient remoteClient) {
        this.remoteClient = remoteClient;
    }

    public DnsResolverResponse resolve(DnsResolverRequest request) {
        try {
            byte[] rawPacket = Base64.getDecoder().decode(request.data());
            byte[] rawResponse = remoteClient.resolve(rawPacket);
            String encoded = Base64.getEncoder().encodeToString(rawResponse);
            return new DnsResolverResponse(encoded);
        } catch (IOException e) {
            throw new DnsResolutionException("Fallo al resolver contra DNS remoto", e);
        }
    }
}