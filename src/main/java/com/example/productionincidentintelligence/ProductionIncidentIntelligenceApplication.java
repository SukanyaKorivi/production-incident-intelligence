package com.example.productionincidentintelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProductionIncidentIntelligenceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductionIncidentIntelligenceApplication.class, args);
	}

}
