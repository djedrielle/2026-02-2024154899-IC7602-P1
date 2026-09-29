package com.tec.dnsapi.controller;

import com.tec.dnsapi.dto.HealthResultResponse;
import com.tec.dnsapi.service.HealthResultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthResultController.class)
@DisplayName("HealthResultController")
class HealthResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthResultService service;

    private HealthResultResponse sample() {
        return new HealthResultResponse(1L, UUID.randomUUID(), "a.example.com", "10.0.0.1", true, 12.5,
                "CR-01", 9.86, -83.91, "CR", "Cartago", "2026-09-29T00:00:00Z");
    }

    @Test
    @DisplayName("GET sin filtros lista todo")
    void listsAll() throws Exception {
        when(service.findAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/health_results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].checker_location_id").value("CR-01"))
                .andExpect(jsonPath("$[0].latency_ms").value(12.5));
    }

    @Test
    @DisplayName("GET filtra por record_name o por target_id")
    void filters() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(service.findByRecordName("a.example.com")).thenReturn(List.of(sample()));
        when(service.findByTargetId(targetId)).thenReturn(List.of(sample(), sample()));

        mockMvc.perform(get("/api/health_results").param("record_name", "a.example.com"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/health_results").param("target_id", targetId.toString()))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("un target_id malformado responde 400")
    void invalidTargetIdIs400() throws Exception {
        mockMvc.perform(get("/api/health_results").param("target_id", "xx"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE por id responde 204, y 404 si no existe")
    void deleteById() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "HealthResult not found")).when(service).delete(99L);

        mockMvc.perform(delete("/api/health_results/5")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/health_results/99")).andExpect(status().isNotFound());
        verify(service).delete(5L);
    }

    @Test
    @DisplayName("DELETE por record_name responde 204; sin el parámetro responde 400")
    void deleteByRecordName() throws Exception {
        mockMvc.perform(delete("/api/health_results").param("record_name", "a.example.com"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/health_results")).andExpect(status().isBadRequest());

        verify(service).deleteByRecordName("a.example.com");
    }
}
