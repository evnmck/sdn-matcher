package com.sdnmatcher.screening;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ScreeningApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScreeningApplication.class, args);
    }
}
