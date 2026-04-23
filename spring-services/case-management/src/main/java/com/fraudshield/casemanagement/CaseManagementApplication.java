package com.fraudshield.casemanagement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
@SpringBootApplication
@EntityScan("com.fraudshield.common.entity")
public class CaseManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(CaseManagementApplication.class, args);
    }
}
