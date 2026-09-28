package com.tec.dnsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tec.dnsapi.dto.IpCountryResponse;
import com.tec.dnsapi.dto.IpToCountryFullResponse;
import com.tec.dnsapi.dto.IpToCountryRequest;
import com.tec.dnsapi.service.IpCountryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IpCountryController.class)
@DisplayName("IpCountryController")
class IpCountryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IpCountryService service;

    private IpToCountryFullResponse sampleFullResponse(Long id) {
        return new IpToCountryFullResponse(
                id,
                "10.0.0.0",
                "10.0.0.255",
                "CR",
                "Costa Rica",
                "San Jose",
                9.93,
                -84.08);
    }

    private IpToCountryRequest sampleRequest() {
        return new IpToCountryRequest(
                "10.0.0.0",
                "10.0.0.255",
                "CR",
                "Costa Rica",
                "San Jose",
                9.93,
                -84.08);
    }

    // -------------------------------------------------------------------------
    // GET /api/ip_country (Dispatch: lookup vs list)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/ip_country")
    class IpCountryDispatch {

        @Test
        @DisplayName("con parámetro ?ip=X devuelve 200 con IpCountryResponse cuando se encuentra el país (Interceptor)")
        void returnsCountryCodeWhenIpParamProvided() throws Exception {
            when(service.findCountryByIp("10.0.0.1")).thenReturn(new IpCountryResponse("CR"));

            mockMvc.perform(get("/api/ip_country").param("ip", "10.0.0.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.country_code").value("CR"));

            verify(service).findCountryByIp("10.0.0.1");
            verify(service, never()).findAll();
        }

        @Test
        @DisplayName("con parámetro ?ip=X devuelve 200 con false cuando la IP no coincide con ningún rango (Interceptor)")
        void returnsFalseWhenIpNotFound() throws Exception {
            when(service.findCountryByIp("192.168.1.1")).thenReturn(false);

            mockMvc.perform(get("/api/ip_country").param("ip", "192.168.1.1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));

            verify(service).findCountryByIp("192.168.1.1");
            verify(service, never()).findAll();
        }

        @Test
        @DisplayName("sin parámetro ip devuelve 200 con la lista completa de rangos (DNS UI)")
        void returnsFullListWhenNoIpParamProvided() throws Exception {
            List<IpToCountryFullResponse> list = List.of(
                    sampleFullResponse(1L),
                    new IpToCountryFullResponse(2L, "20.0.0.0", "20.0.0.255", "US", "United States", "Miami", 25.76,
                            -80.19));
            when(service.findAll()).thenReturn(list);

            mockMvc.perform(get("/api/ip_country"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].country_code").value("CR"))
                    .andExpect(jsonPath("$[0].country_name").value("Costa Rica"))
                    .andExpect(jsonPath("$[1].id").value(2))
                    .andExpect(jsonPath("$[1].country_code").value("US"));

            verify(service).findAll();
            verify(service, never()).findCountryByIp(any());
        }

        @Test
        @DisplayName("sin parámetro ip devuelve lista vacía cuando no hay registros")
        void returnsEmptyListWhenNoRecords() throws Exception {
            when(service.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/ip_country"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/ip_country (CRUD: Create)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/ip_country")
    class CreateRange {

        @Test
        @DisplayName("crea un nuevo rango y devuelve 201 Created con el registro creado")
        void createsRangeAndReturns201() throws Exception {
            IpToCountryRequest req = sampleRequest();
            IpToCountryFullResponse created = sampleFullResponse(10L);

            when(service.create(any(IpToCountryRequest.class))).thenReturn(created);

            mockMvc.perform(post("/api/ip_country")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.start_ip").value("10.0.0.0"))
                    .andExpect(jsonPath("$.end_ip").value("10.0.0.255"))
                    .andExpect(jsonPath("$.country_code").value("CR"))
                    .andExpect(jsonPath("$.country_name").value("Costa Rica"));

            verify(service).create(any(IpToCountryRequest.class));
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/ip_country/{id} (CRUD: Update)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/ip_country/{id}")
    class UpdateRange {

        @Test
        @DisplayName("actualiza un rango existente y devuelve 200 OK con el registro modificado")
        void updatesRangeAndReturns200() throws Exception {
            IpToCountryRequest req = sampleRequest();
            IpToCountryFullResponse updated = sampleFullResponse(1L);

            when(service.update(eq(1L), any(IpToCountryRequest.class))).thenReturn(updated);

            mockMvc.perform(put("/api/ip_country/{id}", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.country_code").value("CR"));

            verify(service).update(eq(1L), any(IpToCountryRequest.class));
        }

        @Test
        @DisplayName("propaga 404 Not Found cuando el id a actualizar no existe")
        void propagates404WhenNotFound() throws Exception {
            IpToCountryRequest req = sampleRequest();
            when(service.update(eq(999L), any(IpToCountryRequest.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Rango no encontrado"));

            mockMvc.perform(put("/api/ip_country/{id}", 999L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound());

            verify(service).update(eq(999L), any(IpToCountryRequest.class));
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/ip_country/{id} (CRUD: Delete)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/ip_country/{id}")
    class DeleteRange {

        @Test
        @DisplayName("elimina un rango existente y devuelve 204 No Content sin cuerpo")
        void deletesRangeAndReturns204() throws Exception {
            doNothing().when(service).delete(1L);

            mockMvc.perform(delete("/api/ip_country/{id}", 1L))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(service).delete(1L);
        }

        @Test
        @DisplayName("propaga 404 Not Found cuando el id a eliminar no existe")
        void propagates404WhenNotFound() throws Exception {
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Rango no encontrado"))
                    .when(service).delete(999L);

            mockMvc.perform(delete("/api/ip_country/{id}", 999L))
                    .andExpect(status().isNotFound());

            verify(service).delete(999L);
        }
    }
}
