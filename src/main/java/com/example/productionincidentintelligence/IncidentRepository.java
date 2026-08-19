package com.example.productionincidentintelligence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository
        extends JpaRepository<Incident,Long> {
}
