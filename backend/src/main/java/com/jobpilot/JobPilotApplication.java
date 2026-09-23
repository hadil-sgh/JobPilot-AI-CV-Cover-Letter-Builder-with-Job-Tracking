package com.jobpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobPilotApplication.class, args);
    }
}
