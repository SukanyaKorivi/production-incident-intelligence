package com.example.productionincidentintelligence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class Event {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private Instant timestamp;
    @Column(nullable = false)
    private String serviceName;
    @Column(nullable = false)
    private String logLevel;
    @Column(nullable = false)
    private String message;
    private Long incidentId;

    public Event(){

    }
    public Event(
            Instant timestamp,String serviceName,String logLevel,String message){
        this.timestamp=timestamp;
        this.serviceName=serviceName;
        this.logLevel=logLevel;
        this.message=message;
    }

    public Long getId(){return id;}

    public Instant getTimestamp(){return timestamp;}
    public void setTimestamp(Instant timestamp){
        this.timestamp=timestamp;
    }

    public String getServiceName(){return serviceName;}
    public void setServiceName(String serviceName){
        this.serviceName=serviceName;
    }

    public String getLogLevel(){return logLevel;}
    public void setLogLevel(String logLevel){
        this.logLevel=logLevel;
    }

    public String getMessage(){return message;}
    public void setMessage(String message){
        this.message=message;
    }
    public Long getIncidentId(){return incidentId;}
    public void setIncidentId(Long incidentId){
        this.incidentId=incidentId;
    }
}
