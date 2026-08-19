package com.example.productionincidentintelligence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class Incident {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
    private String serviceName;
    private String severity;
    private String cause;
    @Column(nullable = false)
    private Instant createdAt;
    private String status;

    public Incident(){

    }
    public Incident(
            String serviceName,String severity,
            String cause,Instant createdAt,String status){
        this.serviceName=serviceName;
        this.cause=cause;
        this.severity=severity;
        this.status=status;
        this.createdAt=createdAt;
    }

    public Long getId(){return id;}
    public String getServiceName(){return serviceName;}
    public String getSeverity(){return severity;}
    public String getCause(){return cause;}
    public String getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;}

    public void setServiceName(String serviceName){
        this.serviceName=serviceName;
    }
    public void setSeverity(String severity){
        this.severity=severity;
    }
    public void setCause(String cause){
        this.cause=cause;
    }
    public void setStatus(String status){
        this.status=status;
    }
    public void setCreatedAt(Instant createdAt){
        this.createdAt=createdAt;
    }
}
