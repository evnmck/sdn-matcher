package com.sdnmatcher.screening.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "screening")
public record ScreeningProperties(String accountsPath, String sdnPath, double nameThreshold) {
}
