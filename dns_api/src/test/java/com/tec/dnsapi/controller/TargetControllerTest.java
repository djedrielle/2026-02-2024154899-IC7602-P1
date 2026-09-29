package com.tec.dnsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tec.dnsapi.dto.TargetRequest;
import com.tec.dnsapi.dto.TargetResponse;
import com.tec.dnsapi.exception.InvalidRequestException;
import com.tec.dnsapi.service.TargetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TargetController.class)
@DisplayName("TargetController")
class TargetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TargetService service;

    private final UUID id = UUID.randomUUID();

    private TargetResponse sampleResponse() {
        return new TargetResponse(id, "a.example.com", "10.0.0.1", 80, "TCP", 1000, 2, null, null, null, null);
    }

    private TargetRequest sampleRequest() {
        return new TargetRequest("a.example.com", "10.0.0.1", 80, "TCP", 1000, 2, null, null, null, null);
    }

    @Test
    @DisplayName("GET /api/targets lista todos, o filtra con ?record_name")
    void listsAndFilters() throws Exception {
        when(service.findAll()).thenReturn(List.of(sampleResponse()));
        when(service.findByRecordName("a.example.com")).thenReturn(List.of(sampleResponse(), sampleResponse()));

        mockMvc.perform(get("/api/targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/targets").param("record_name", "a.example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        verify(service).findAll();
    }

    @Test
    @DisplayName("POST /api/targets responde 201 con el target creado")
    void createReturns201() throws Exception {
        when(service.create(any(TargetRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/targets").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.record_name").value("a.example.com"));
    }

    @Test
    @DisplayName("PUT /api/targets/{id} responde 200 y 404 si no existe")
    void updateOkAndNotFound() throws Exception {
        when(service.update(eq(id), any(TargetRequest.class))).thenReturn(sampleResponse());
        UUID missing = UUID.randomUUID();
        when(service.update(eq(missing), any(TargetRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not found"));
        String body = objectMapper.writeValueAsString(sampleRequest());

        mockMvc.perform(put("/api/targets/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/targets/" + missing).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Target not found"));
    }

    @Test
    @DisplayName("DELETE por id y por record_name responden 204")
    void deletes() throws Exception {
        mockMvc.perform(delete("/api/targets/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/targets").param("record_name", "a.example.com")).andExpect(status().isNoContent());

        verify(service).delete(id);
        verify(service).deleteByRecordName("a.example.com");
    }

    @Test
    @DisplayName("DELETE /api/targets sin record_name responde 400 y no borra nada")
    void deleteWithoutRecordNameIs400() throws Exception {
        mockMvc.perform(delete("/api/targets"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el parámetro obligatorio: record_name"));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("un id que no es UUID responde 400")
    void invalidUuidIs400() throws Exception {
        mockMvc.perform(delete("/api/targets/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Valor inválido para el parámetro: id"));
    }

    @Test
    @DisplayName("un JSON malformado responde 400 con el formato de error de la API")
    void malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/api/targets").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El cuerpo de la petición no es un JSON válido"));
    }

    @Test
    @DisplayName("errores de validación responden 400; duplicados 409; referencias rotas 400")
    void mapsServiceErrors() throws Exception {
        String body = objectMapper.writeValueAsString(sampleRequest());
        when(service.create(any(TargetRequest.class)))
                .thenThrow(new InvalidRequestException("'port' debe estar entre 1 y 65535"))
                .thenThrow(new DataIntegrityViolationException("x", new RuntimeException("ERROR: duplicate key value")))
                .thenThrow(new DataIntegrityViolationException("x", new RuntimeException("violates foreign key constraint")))
                .thenThrow(new DataIntegrityViolationException("x", new RuntimeException("violates check constraint")));

        mockMvc.perform(post("/api/targets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("'port' debe estar entre 1 y 65535"));
        mockMvc.perform(post("/api/targets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/targets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Hace referencia a un elemento que no existe"));
        mockMvc.perform(post("/api/targets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Los datos no cumplen las restricciones de la base de datos"));
    }
}
