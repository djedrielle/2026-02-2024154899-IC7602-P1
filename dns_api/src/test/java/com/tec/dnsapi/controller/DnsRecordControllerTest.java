package com.tec.dnsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tec.dnsapi.config.CorsConfig;
import com.tec.dnsapi.dto.RecordRequest;
import com.tec.dnsapi.dto.RecordResponse;
import com.tec.dnsapi.service.DnsRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DnsRecordController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = "dns.ui.origin=http://localhost:3000")
@DisplayName("DnsRecordController")
class DnsRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DnsRecordService service;

    private final List<Map<String, Object>> sampleIps = List.of(Map.of("ip", "1.2.3.4", "healthy", true));

    private RecordResponse sampleResponse() {
        return new RecordResponse("example.com", "single", 300, sampleIps);
    }

    @Test
    @DisplayName("permite preflight desde DNS UI")
    void allowsDnsUiCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/records")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    // -------------------------------------------------------------------------
    // GET /api/exists
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/exists")
    class Exists {

        @Test
        @DisplayName("devuelve 200 con el registro cuando el dominio existe")
        void returnsRecordWhenFound() throws Exception {
            when(service.findByDomain("example.com")).thenReturn(sampleResponse());

            mockMvc.perform(get("/api/exists").param("domain", "example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("example.com"))
                    .andExpect(jsonPath("$.type").value("single"))
                    .andExpect(jsonPath("$.ttl").value(300));
        }

        @Test
        @DisplayName("devuelve 200 con false cuando el dominio no existe")
        void returnsFalseWhenNotFound() throws Exception {
            when(service.findByDomain("ghost.com")).thenReturn(false);

            mockMvc.perform(get("/api/exists").param("domain", "ghost.com"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/records
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/records")
    class FindAll {

        @Test
        @DisplayName("devuelve 200 con lista vacía cuando no hay registros")
        void returnsEmptyList() throws Exception {
            when(service.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/records"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @DisplayName("devuelve 200 con todos los registros")
        void returnsRecords() throws Exception {
            when(service.findAll()).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/records"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("example.com"))
                    .andExpect(jsonPath("$[0].type").value("single"));
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/records
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/records")
    class Create {

        @Test
        @DisplayName("devuelve 201 con el registro creado")
        void returnsCreatedRecord() throws Exception {
            RecordRequest req = new RecordRequest("newdomain.com", "single", 300, sampleIps);
            RecordResponse created = new RecordResponse("newdomain.com", "single", 300, sampleIps);
            when(service.create(any(RecordRequest.class))).thenReturn(created);

            mockMvc.perform(post("/api/records")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("newdomain.com"));
        }

        @Test
        @DisplayName("devuelve 409 cuando el dominio ya existe")
        void returnsConflictWhenDuplicate() throws Exception {
            RecordRequest req = new RecordRequest("existing.com", "single", 300, sampleIps);
            when(service.create(any(RecordRequest.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe"));

            mockMvc.perform(post("/api/records")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/records/{name}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/records/{name}")
    class Update {

        @Test
        @DisplayName("devuelve 200 con el registro actualizado")
        void returnsUpdatedRecord() throws Exception {
            List<Map<String, Object>> newIps = List.of(Map.of("ip", "9.9.9.9", "healthy", true));
            RecordRequest req = new RecordRequest("example.com", "multi", 600, newIps);
            RecordResponse updated = new RecordResponse("example.com", "multi", 600, newIps);
            when(service.update(eq("example.com"), any(RecordRequest.class))).thenReturn(updated);

            mockMvc.perform(put("/api/records/example.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("multi"))
                    .andExpect(jsonPath("$.ttl").value(600));
        }

        @Test
        @DisplayName("devuelve 404 cuando el dominio no existe")
        void returnsNotFoundWhenMissing() throws Exception {
            RecordRequest req = new RecordRequest("ghost.com", "single", 300, sampleIps);
            when(service.update(eq("ghost.com"), any(RecordRequest.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe"));

            mockMvc.perform(put("/api/records/ghost.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/records/{name}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/records/{name}")
    class Delete {

        @Test
        @DisplayName("devuelve 204 sin cuerpo cuando el registro se elimina")
        void returnsNoContent() throws Exception {
            doNothing().when(service).delete("example.com");

            mockMvc.perform(delete("/api/records/example.com"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("devuelve 404 cuando el dominio no existe")
        void returnsNotFoundWhenMissing() throws Exception {
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe"))
                    .when(service).delete("ghost.com");

            mockMvc.perform(delete("/api/records/ghost.com"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").exists());
        }
    }
}
