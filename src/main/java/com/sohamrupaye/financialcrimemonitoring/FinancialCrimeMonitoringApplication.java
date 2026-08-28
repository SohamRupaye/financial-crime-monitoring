package com.sohamrupaye.financialcrimemonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@code @ConfigurationPropertiesScan} picks up {@code AmlProperties}. Without it
 * the record is never bound and the rules get nothing to work with — a
 * {@code @ConfigurationProperties} class is not a component and is not found by
 * component scanning.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FinancialCrimeMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinancialCrimeMonitoringApplication.class, args);
    }

}
