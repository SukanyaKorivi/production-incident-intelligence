package com.example.productionincidentintelligence;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for EventController, covering the real validation
 * rule in EventController.createEvent(): a missing/blank serviceName
 * returns 400, otherwise the event is persisted and 201 is returned.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventRepository eventRepository;

    @MockitoBean
    private EventService eventService;

    @Test
    void rejectsEventWithoutServiceName() throws Exception {
        Event event = new Event(Instant.now(), null, "ERROR", "Something failed");

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEventWithBlankServiceName() throws Exception {
        Event event = new Event(Instant.now(), "   ", "ERROR", "Something failed");

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsEventWhenServiceNameProvided() throws Exception {
        Event event = new Event(Instant.now(), "payment-service", "ERROR",
                "DATABASE CONNECTION FAILURE:Pool exhausted");
        when(eventRepository.save(any(Event.class))).thenReturn(event);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated());
    }
}
