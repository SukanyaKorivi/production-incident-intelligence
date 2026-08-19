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
        Long getErrorCount();     // Maps automatically to the alias we will set in the query
    }



@Query(value="SELECT service_Name AS serviceName,COUNT(log_Level) AS errorCount FROM event WHERE log_Level= 'ERROR' AND incident_Id IS NULL AND timestamp >= :cutoffTime Group By service_Name",nativeQuery=true)
List<ServiceErrorCount> findErrorCountGroupByService(@Param("cutoffTime") Instant cutoffTime);
}
