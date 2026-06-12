package com.voltvanguard.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * VoltVanguard Core — Spring Boot entry point.
 *
 * <p>EnableScheduling activates the task expiry cron job in TaskServiceImpl.</p>
 * <p>PageSerializationMode.VIA_DTO ensures Page responses serialize cleanly as JSON.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class VoltVanguardCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoltVanguardCoreApplication.class, args);
    }
}
