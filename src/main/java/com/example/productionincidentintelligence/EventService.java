package com.example.productionincidentintelligence;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EventService {
    private final EventRepository eventRepository;
    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }
    @Scheduled(fixedRate = 5000)
    public void findErrorLogs() {

        Instant threeSecondsAgo = Instant.now().minusSeconds(60);
        List<EventRepository.ServiceErrorCount> errors = eventRepository.findErrorCountGroupByService(threeSecondsAgo);


        for(EventRepository.ServiceErrorCount error:errors){
            String name=error.getServiceName();
            Long count=error.getErrorCount();

            if (count >= 10) {
                System.out.println("Potential incident detected in "+name+" : "+count+" errors found in last 60 seconds");
            }
        }
    }
}
