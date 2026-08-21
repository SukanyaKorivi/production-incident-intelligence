package com.example.productionincidentintelligence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for IncidentController, covering the thin list view
 * (GET /incidents) and the detail view with nested evidence
 * (GET /incidents/{id}), including the 404 branch when no incident
 * matches the given id.
 */
@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentRepository incidentRepository;

    @MockitoBean
    private EventRepository eventRepository;

    @Test
    void returnsAllIncidents() throws Exception {
        Incident incident = new Incident("payment-service", "CRITICAL",
                "DATABASE CONNECTION FAILURE:Pool exhausted", Instant.now(), "OPEN");
        when(incidentRepository.findAll()).thenReturn(List.of(incident));

        mockMvc.perform(get("/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceName").value("payment-service"));
    }

    @Test
    void returns404WhenIncidentNotFound() throws Exception {
        when(incidentRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/incidents/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsIncidentWithEvidenceWhenFound() throws Exception {
        Incident incident = new Incident("auth-service", "CRITICAL",
                "AUTHENTICATION SERVER TIMEOUT", Instant.now(), "OPEN");
        Event evidenceEvent = new Event(Instant.now(), "auth-service", "ERROR",
                "AUTHENTICATION SERVER TIMEOUT");

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(eventRepository.findCorrelatedErrorByIncidentId(any())).thenReturn(List.of(evidenceEvent));

        mockMvc.perform(get("/incidents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.serviceName").value("auth-service"))
                .andExpect(jsonPath("$.Evidence[0].serviceName").value("auth-service"));
    }
}
