package com.tec.dnsapi.service;

import com.tec.dnsapi.client.DnsRemoteClient;
import com.tec.dnsapi.dto.DnsResolverRequest;
import com.tec.dnsapi.dto.DnsResolverResponse;
import com.tec.dnsapi.exception.DnsResolutionException;
import com.tec.dnsapi.exception.InvalidDnsPacketException;
import com.tec.dnsapi.model.DnsRecord;
import com.tec.dnsapi.repository.DnsRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class DnsResolverService {

    private static final Logger log = LoggerFactory.getLogger(DnsResolverService.class);

    private final DnsRemoteClient remoteClient;
    private final DnsRecordRepository recordRepository;

    public DnsResolverService(DnsRemoteClient remoteClient, DnsRecordRepository recordRepository) {
        this.remoteClient = remoteClient;
        this.recordRepository = recordRepository;
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
        byte[] rawPacket;
        try {
            rawPacket = Base64.getDecoder().decode(request.data());
        } catch (IllegalArgumentException e) {
            throw new InvalidDnsPacketException("El campo 'data' no es BASE64 valido", e);
        }
        try {
            new Message(rawPacket);
        } catch (IOException | RuntimeException e) {
            throw new InvalidDnsPacketException("El campo 'data' no contiene un paquete DNS valido", e);
        }
        return rawPacket;
    }

    /**
     * Envia el paquete ya decodificado al DNS remoto por UDP, guarda el dominio
     * resuelto en la base de datos y devuelve la respuesta codificada en BASE64.
     */
    public DnsResolverResponse resolveDecoded(byte[] rawPacket) {
        try {
            byte[] rawResponse = remoteClient.resolve(rawPacket);
            saveResolvedRecord(rawResponse);
            return new DnsResolverResponse(
                    Base64.getEncoder().encodeToString(rawResponse));
        } catch (IOException e) {
            throw new DnsResolutionException("Fallo al resolver contra el DNS remoto", e);
        }
    }

    /**
     * Parsea la respuesta DNS, extrae las IPs del registro y lo persiste en la BD.
     */
    private void saveResolvedRecord(byte[] rawResponse) {
        try {
            Message responseMsg = new Message(rawResponse);
            if (responseMsg.getRcode() != Rcode.NOERROR) {
                return;
            }

            Record question = responseMsg.getQuestion();
            if (question == null) {
                return;
            }

            String domainName = question.getName().toString(true).toLowerCase().trim();

            List<Map<String, Object>> ips = new ArrayList<>();
            int ttl = 300;

            for (Record record : responseMsg.getSection(Section.ANSWER)) {
                if (record instanceof ARecord aRecord) {
                    ips.add(Map.of("ip", aRecord.getAddress().getHostAddress(), "healthy", true));
                    ttl = (int) aRecord.getTTL();
                }
            }

            if (ips.isEmpty()) {
                return;
            }

            String type = ips.size() > 1 ? "multi" : "single";

            // Un registro existente puede haberlo definido un usuario (con su tipo, pesos o países):
            // la resolución recursiva nunca lo sobrescribe.
            if (recordRepository.existsById(domainName)) {
                log.info("El dominio {} ya existe en la BD; no se sobrescribe", domainName);
                return;
            }
            recordRepository.save(new DnsRecord(domainName, type, ttl, ips));
            log.info("Dominio resuelto guardado en la BD: {} (tipo: {}, ttl: {}, ips: {})",
                    domainName, type, ttl, ips.size());
        } catch (Exception e) {
            log.error("No se pudo guardar en BD el dominio resuelto: {}", e.getMessage(), e);
        }
    }
}