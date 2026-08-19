package com.example.productionincidentintelligence;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class Controller {

    private final EventRepository eventRepository;

    public Controller(EventRepository eventRepository){
        this.eventRepository = eventRepository;
    }

    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestBody Event event){
        if (event.getServiceName() == null || event.getServiceName().isBlank()) {
            return ResponseEntity.badRequest().body("serviceName is required");
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        Event savedEvent= eventRepository.save(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEvent);
    }
}
