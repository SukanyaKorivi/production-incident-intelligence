package com.example.productionincidentintelligence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

public interface EventRepository
      extends JpaRepository<Event,Long> {
    public interface ServiceErrorCount {
        String getServiceName();  // Maps automatically to 'serviceName'
        Long getErrorCount();
        String getMessage();// Maps automatically to the alias we will set in the query
    }



@Query(value="SELECT service_Name AS serviceName,message,COUNT(log_Level) AS errorCount FROM event WHERE log_Level= 'ERROR' AND incident_Id IS NULL AND timestamp >= :cutoffTime Group By service_Name,message",nativeQuery=true)
List<ServiceErrorCount> findErrorCountGroupByService(@Param("cutoffTime") Instant cutoffTime);

@Query(value = "SELECT * FROM event WHERE log_level = 'ERROR' AND incident_id IS NULL AND timestamp >= :cutoffTime", nativeQuery = true)
List<Event> findUncorrelatedErrorsAfter(@Param("cutoffTime") Instant cutoffTime);

@Query(value="SELECT * FROM event WHERE incident_id = :newIncidentId",nativeQuery = true)
List<Event> findCorrelatedErrorByIncidentId(@Param("newIncidentId") Long newIncidentId);


}
