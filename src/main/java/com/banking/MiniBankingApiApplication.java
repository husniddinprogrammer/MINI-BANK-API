package com.banking;

import com.banking.config.ApplicationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Entry point for the Mini Banking REST API.
 *
 * <p>{@code @EnableConfigurationProperties} binds {@link ApplicationProperties}
 * at startup with full JSR-303 validation — if required properties are missing
 * or malformed, the application fails fast before serving any requests.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@SpringBootApplication
@EnableConfigurationProperties(ApplicationProperties.class)
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class MiniBankingApiApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments (passed to Spring Boot)
     */
    public static void main(String[] args) {
        SpringApplication.run(MiniBankingApiApplication.class, args);
    }
}
