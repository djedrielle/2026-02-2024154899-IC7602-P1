package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.DnsResolverRequest;
import com.tec.dnsapi.dto.DnsResolverResponse;
import com.tec.dnsapi.exception.DnsResolutionException;
import com.tec.dnsapi.exception.InvalidDnsPacketException;
import com.tec.dnsapi.service.DnsResolverService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DnsResolverController.class)
@Import(DnsResolverControllerTest.ExecutorConfig.class)
@DisplayName("DnsResolverController")
class DnsResolverControllerTest {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    @TestConfiguration
    static class ExecutorConfig {
        @Bean(name = "dnsResolverExecutor")
        ExecutorService dnsResolverExecutor() {
            return EXECUTOR;
        }
    }

    @AfterAll
    static void shutdown() {
        EXECUTOR.shutdownNow();
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DnsResolverService service;

    private static final String BODY = "{\"data\":\"AAAA\"}";

    @Test
    @DisplayName("POST /api/dns_resolver responde 200 con la respuesta DNS en BASE64")
    void resolvesPacket() throws Exception {
        when(service.decodeAndValidate(any(DnsResolverRequest.class))).thenReturn(new byte[] { 1, 2 });
        when(service.resolveDecoded(any())).thenReturn(new DnsResolverResponse("UkVTUA=="));

        MvcResult started = mockMvc.perform(post("/api/dns_resolver").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("UkVTUA=="));
    }

    @Test
    @DisplayName("un paquete inválido responde 400 sin usar el pool")
    void invalidPacketIs400() throws Exception {
        when(service.decodeAndValidate(any())).thenThrow(new InvalidDnsPacketException("no contiene un paquete DNS valido"));

        mockMvc.perform(post("/api/dns_resolver").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("no contiene un paquete DNS valido"));
    }

    @Test
    @DisplayName("si el DNS remoto falla responde 502")
    void upstreamFailureIs502() throws Exception {
        when(service.decodeAndValidate(any())).thenReturn(new byte[] { 1 });
        when(service.resolveDecoded(any())).thenThrow(new DnsResolutionException("Fallo al resolver", new IOException("timeout")));

        MvcResult started = mockMvc.perform(post("/api/dns_resolver").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isBadGateway());
    }
}
