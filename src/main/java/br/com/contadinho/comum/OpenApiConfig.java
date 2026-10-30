package br.com.contadinho.comum;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato que gera o cliente TypeScript do app (RNF-07).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI contrato() {
		return new OpenAPI()
			.info(new Info().title("Contadinho API").version("v1"))
			// Servidor fixo: sem ele o springdoc grava a porta aleatória do teste e o openapi.json muda a cada build.
			.servers(List.of(new Server().url("/")))
			.components(new Components().addSecuritySchemes("bearer",
				new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList("bearer"));
	}

}
