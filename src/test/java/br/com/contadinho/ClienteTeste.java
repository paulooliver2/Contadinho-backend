package br.com.contadinho;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Chama a API de verdade pela porta do teste e devolve qualquer status, sem lançar exceção.
 */
public class ClienteTeste {

	public static final String SENHA = "senha-boa-123";

	private final RestClient http;

	private final JdbcTemplate jdbc;

	public ClienteTeste(String porta, JdbcTemplate jdbc) {
		this.http = RestClient.builder()
			.baseUrl("http://localhost:" + porta)
			.defaultStatusHandler(status -> true, (requisicao, resposta) -> {
			})
			.build();
		this.jdbc = jdbc;
	}

	/** {@code corpo} é o JSON lido como Map ou List, ou null quando não há corpo. */
	public record Resposta(int status, Object corpo, HttpHeaders cabecalhos) {

		@SuppressWarnings("unchecked")
		public Map<String, Object> objeto() {
			return (Map<String, Object>) corpo;
		}

		@SuppressWarnings("unchecked")
		public List<Map<String, Object>> lista() {
			return (List<Map<String, Object>>) corpo;
		}

		public String texto(String campo) {
			return (String) objeto().get(campo);
		}
	}

	public Resposta post(String caminho, Object corpo) {
		return post(caminho, corpo, null);
	}

	/** Com {@code corpo} null, envia o POST sem corpo (ações como arquivar). */
	public Resposta post(String caminho, Object corpo, String tokenAcesso) {
		var pedido = http.post().uri(caminho).headers(h -> autorizar(h, tokenAcesso));
		if (corpo != null) {
			pedido.contentType(MediaType.APPLICATION_JSON).body(corpo);
		}
		var r = pedido.retrieve().toEntity(Object.class);
		return new Resposta(r.getStatusCode().value(), r.getBody(), r.getHeaders());
	}

	public Resposta get(String caminho, String tokenAcesso) {
		var r = http.get().uri(caminho).headers(h -> autorizar(h, tokenAcesso))
			.retrieve().toEntity(Object.class);
		return new Resposta(r.getStatusCode().value(), r.getBody(), r.getHeaders());
	}

	public Resposta put(String caminho, Object corpo, String tokenAcesso) {
		var r = http.put().uri(caminho).headers(h -> autorizar(h, tokenAcesso))
			.contentType(MediaType.APPLICATION_JSON).body(corpo)
			.retrieve().toEntity(Object.class);
		return new Resposta(r.getStatusCode().value(), r.getBody(), r.getHeaders());
	}

	public Resposta delete(String caminho, String tokenAcesso) {
		var r = http.delete().uri(caminho).headers(h -> autorizar(h, tokenAcesso))
			.retrieve().toEntity(Object.class);
		return new Resposta(r.getStatusCode().value(), r.getBody(), r.getHeaders());
	}

	private static void autorizar(HttpHeaders cabecalhos, String tokenAcesso) {
		if (tokenAcesso != null) {
			cabecalhos.setBearerAuth(tokenAcesso);
		}
	}

	public Resposta conta(String tokenAcesso) {
		return get("/api/conta", tokenAcesso);
	}

	public static String emailNovo() {
		return "dono-" + UUID.randomUUID() + "@exemplo.com";
	}

	public Resposta cadastrar(String email) {
		return post("/api/auth/cadastro", Map.of("nome", "Paulo", "email", email, "senha", SENHA));
	}

	/** Cria uma conta nova e devolve o token de acesso dela. */
	public String novoDono() {
		return cadastrar(emailNovo()).texto("tokenAcesso");
	}

	public Resposta entrar(String email, String senha) {
		return post("/api/auth/entrar", Map.of("email", email, "senha", senha));
	}

	/** Grava senhas erradas como se tivessem acontecido há tantos minutos (RN-03). */
	public void errosNoPassado(String email, int... minutosAtras) {
		for (int minutos : minutosAtras) {
			jdbc.update("insert into tentativa_login (email, sucesso, ocorrida_em) values (?, false, ?)", email,
					Instant.now().minus(minutos, ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC));
		}
	}

}
