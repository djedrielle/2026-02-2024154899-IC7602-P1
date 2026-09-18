package com.tec.dnsapi.service;

import com.tec.dnsapi.client.DnsRemoteClient;
import com.tec.dnsapi.dto.DnsResolverRequest;
import com.tec.dnsapi.dto.DnsResolverResponse;
import com.tec.dnsapi.exception.DnsResolutionException;
import com.tec.dnsapi.exception.InvalidDnsPacketException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;

@Service
public class DnsResolverService {

    private final DnsRemoteClient remoteClient;

    public DnsResolverService(DnsRemoteClient remoteClient) {
        this.remoteClient = remoteClient;
    }

    /**
     * Valida y decodifica el paquete BASE64. Se ejecuta de forma sincrona
     * antes de delegar al pool: es una operacion instantanea y, si falla,
     * evita ocupar un hilo del executor.
     */
    public byte[] decodeAndValidate(DnsResolverRequest request) {
        if (request == null || request.data() == null || request.data().isBlank()) {
            throw new InvalidDnsPacketException(
                    "El campo 'data' es obligatorio y no puede estar vacio");
        }
        try {
            return Base64.getDecoder().decode(request.data());
        } catch (IllegalArgumentException e) {
            throw new InvalidDnsPacketException("El campo 'data' no es BASE64 valido", e);
        }
    }

    /**
     * Envia el paquete ya decodificado al DNS remoto por UDP y devuelve
     * la respuesta codificada en BASE64. Operacion bloqueante de I/O.
     */
    public DnsResolverResponse resolveDecoded(byte[] rawPacket) {
        try {
            byte[] rawResponse = remoteClient.resolve(rawPacket);
            return new DnsResolverResponse(
                    Base64.getEncoder().encodeToString(rawResponse));
        } catch (IOException e) {
            throw new DnsResolutionException("Fallo al resolver contra el DNS remoto", e);
        }
    }
}