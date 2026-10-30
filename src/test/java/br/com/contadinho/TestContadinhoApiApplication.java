package br.com.contadinho;

import org.springframework.boot.SpringApplication;

/**
 * Sobe a API com um Postgres do Testcontainers, sem precisar do compose.yaml.
 */
public class TestContadinhoApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(ContadinhoApiApplication::main)
			.with(TestcontainersConfiguration.class)
			.withAdditionalProfiles("dev")
			.run(args);
	}

}
