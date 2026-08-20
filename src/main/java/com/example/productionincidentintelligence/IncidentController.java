package com.example.productionincidentintelligence;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class IncidentController {

    private final IncidentRepository incidentRepository;
    private final EventRepository eventRepository;

    public IncidentController(IncidentRepository incidentRepository,
                              EventRepository eventRepository){
        this.incidentRepository=incidentRepository;
        this.eventRepository=eventRepository;
    }

    @GetMapping("/incidents")
    public ResponseEntity<List<Incident>> allIncidents(){
        List<Incident> incidents=incidentRepository.findAll();
        return ResponseEntity.ok(incidents);
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<?> getIncidentById(@PathVariable Long id){
        Map<String,Object> result=new HashMap<>();
       Optional<Incident> incidentOpt=incidentRepository.findById(id);


       if(incidentOpt.isPresent()){
           Incident incident=incidentOpt.get();
           List<Event> relatedEvents=eventRepository.findCorrelatedErrorByIncidentId(incident.getId());
           result.put("incident",incident);
           result.put("Evidence",relatedEvents);
           return ResponseEntity.ok(result);

       }
       else{
           return ResponseEntity.notFound().build();
       }


    }
}
