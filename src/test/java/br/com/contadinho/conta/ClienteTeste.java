package br.com.contadinho.conta;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Chama a API de verdade pela porta do teste e devolve qualquer status, sem lançar exceção.
 */
class ClienteTeste {

	static final String SENHA = "senha-boa-123";

	private final RestClient http;

	private final JdbcTemplate jdbc;

	ClienteTeste(String porta, JdbcTemplate jdbc) {
		this.http = RestClient.builder()
			.baseUrl("http://localhost:" + porta)
			.defaultStatusHandler(status -> true, (requisicao, resposta) -> {
			})
			.build();
		this.jdbc = jdbc;
	}

	record Resposta(int status, Map<String, Object> corpo, HttpHeaders cabecalhos) {

		String texto(String campo) {
			return (String) corpo.get(campo);
		}
	}

	Resposta post(String caminho, Map<String, ?> corpo) {
		var r = http.post().uri(caminho).contentType(MediaType.APPLICATION_JSON).body(corpo)
			.retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		return new Resposta(r.getStatusCode().value(), r.getBody(), r.getHeaders());
	}

	Resposta conta(String tokenAcesso) {
		var r = http.get().uri("/api/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAcesso)
			.retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		return new Resposta(r.getStatusCode().value(), r.getBody(), r.getHeaders());
	}

	static String emailNovo() {
		return "dono-" + UUID.randomUUID() + "@exemplo.com";
	}

	Resposta cadastrar(String email) {
		return post("/api/auth/cadastro", Map.of("nome", "Paulo", "email", email, "senha", SENHA));
	}

	Resposta entrar(String email, String senha) {
		return post("/api/auth/entrar", Map.of("email", email, "senha", senha));
	}

	/** Grava senhas erradas como se tivessem acontecido há tantos minutos (RN-03). */
	void errosNoPassado(String email, int... minutosAtras) {
		for (int minutos : minutosAtras) {
			jdbc.update("insert into tentativa_login (email, sucesso, ocorrida_em) values (?, false, ?)", email,
					Instant.now().minus(minutos, ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC));
		}
	}

}
