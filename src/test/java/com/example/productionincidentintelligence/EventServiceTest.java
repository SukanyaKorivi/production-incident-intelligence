package com.example.productionincidentintelligence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EventService, covering the actual detection threshold
 * (>=10 errors in the 60-second window) and the cleanup delegation,
 * matching the real implementation in EventService.findErrorLogs()
 * and EventService.cleanUpLogs().
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void createsIncidentWhenErrorCountMeetsThreshold() {
        EventRepository.ServiceErrorCount errorCount = mock(EventRepository.ServiceErrorCount.class);
        when(errorCount.getServiceName()).thenReturn("payment-service");
        when(errorCount.getErrorCount()).thenReturn(10L);
        when(errorCount.getMessage()).thenReturn("DATABASE CONNECTION FAILURE:Pool exhausted");

        when(eventRepository.findErrorCountGroupByService(any(Instant.class)))
                .thenReturn(List.of(errorCount));
        when(eventRepository.findUncorrelatedErrorsAfter(any(Instant.class)))
                .thenReturn(List.of(new Event(Instant.now(), "payment-service", "ERROR",
                        "DATABASE CONNECTION FAILURE:Pool exhausted")));

        Incident savedIncident = new Incident("payment-service", "CRITICAL",
                "DATABASE CONNECTION FAILURE:Pool exhausted", Instant.now(), "OPEN");
        when(incidentRepository.save(any(Incident.class))).thenReturn(savedIncident);

        eventService.findErrorLogs();

        verify(incidentRepository, times(1)).save(any(Incident.class));
        verify(eventRepository, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    void doesNotCreateIncidentWhenErrorCountBelowThreshold() {
        EventRepository.ServiceErrorCount errorCount = mock(EventRepository.ServiceErrorCount.class);
        when(errorCount.getServiceName()).thenReturn("auth-service");
        when(errorCount.getErrorCount()).thenReturn(3L);

        when(eventRepository.findErrorCountGroupByService(any(Instant.class)))
                .thenReturn(List.of(errorCount));
        when(eventRepository.findUncorrelatedErrorsAfter(any(Instant.class)))
                .thenReturn(List.of());

        eventService.findErrorLogs();

        verify(incidentRepository, never()).save(any(Incident.class));
        verify(eventRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void cleanUpLogsDelegatesToRepositoryWithCutoff() {
        when(eventRepository.deleteByTimestampBeforeAndIncidentIdNull(any(Instant.class)))
                .thenReturn(5);

        eventService.cleanUpLogs();

        verify(eventRepository, times(1)).deleteByTimestampBeforeAndIncidentIdNull(any(Instant.class));
    }
}
