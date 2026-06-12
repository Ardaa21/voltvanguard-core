package com.voltvanguard.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI 3 configuration.
 * UI available at: /api/v1/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;

    @Bean
    public OpenAPI voltVanguardOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("VoltVanguard Core API")
                .description("""
                    REST API for the VoltVanguard Autonomous EV Intelligence & Grid Orchestrator.

                    Manages the complete lifecycle of Electric Vehicles, Charging Stations,
                    and Autonomous Tasks dispatched to the Python AI agent layer.
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("VoltVanguard Engineering")
                    .email("engineering@voltvanguard.io"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT"))
            )
            .servers(List.of(
                new Server().url("http://localhost:8080" + contextPath).description("Local development"),
                new Server().url("https://api.voltvanguard.io/v1").description("Production")
            ));
    }
}
