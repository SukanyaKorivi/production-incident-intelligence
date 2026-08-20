package com.example.productionincidentintelligence;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final IncidentRepository incidentRepository;
    public EventService(EventRepository eventRepository,IncidentRepository incidentRepository) {
        this.eventRepository = eventRepository;
        this.incidentRepository=incidentRepository;
    }
    @Scheduled(fixedRate = 5000)
    public void findErrorLogs() {

        Instant threeSecondsAgo = Instant.now().minusSeconds(60);
        List<EventRepository.ServiceErrorCount> errors = eventRepository.findErrorCountGroupByService(threeSecondsAgo);
        List<Event> eventList=eventRepository.findUncorrelatedErrorsAfter(threeSecondsAgo);


        for(EventRepository.ServiceErrorCount error:errors){
            String name=error.getServiceName();
            Long count=error.getErrorCount();

            if (count >= 10) {
                System.out.println("Potential incident detected in "+name+" : "+count+" errors found in last 60 seconds");

                Incident createIncident=new Incident(
                                error.getServiceName(),"CRITICAL",
                                error.getMessage(),Instant.now(), "OPEN");

                Incident savedIncident=incidentRepository.save(createIncident);
                for(Event event:eventList){
                    event.setIncidentId(savedIncident.getId());
                }
                eventRepository.saveAllAndFlush(eventList);
                }
            else{
                System.out.println("warning suppressed due to Low severity in "+name);
            }

        }


    }
    @Scheduled(fixedRate = 500000)
    public void deletelogs(){
        Instant cutoffTime=Instant.now().minusSeconds(60);
        int logCount=eventRepository.deleteByTimestampBeforeAndIncidentIdNull(cutoffTime);
        System.out.println("CLEAN UP: Deleted "+logCount+" stale events");
    }

}
