package com.fraudshield.orchestrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
@SpringBootApplication
@EntityScan("com.fraudshield.common.entity")
public class FraudOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudOrchestratorApplication.class, args);
    }
}
