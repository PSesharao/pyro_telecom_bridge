package com.telecombridge.gateway;

import com.telecombridge.gateway.config.DiameterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Telecom-Bridge Gateway application.
 */
@SpringBootApplication
@EnableConfigurationProperties(DiameterProperties.class)
@EnableScheduling
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
