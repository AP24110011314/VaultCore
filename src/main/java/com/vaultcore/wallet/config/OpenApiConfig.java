package com.vaultcore.wallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vaultCoreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VaultCore API")
                        .description("High-performance double-entry virtual credit ledger service")
                        .version("1.0.0"));
    }
}
