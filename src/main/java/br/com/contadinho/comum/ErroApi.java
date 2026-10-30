package br.com.contadinho.comum;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Erro de negócio devolvido como ProblemDetail (RFC 9457). O app mostra o {@code detail} ao usuário,
 * então escreva-o como frase para gente, em português.
 */
public class ErroApi extends ErrorResponseException {

	private final HttpHeaders cabecalhos;

	public ErroApi(HttpStatus status, String detalhe) {
		this(status, detalhe, HttpHeaders.EMPTY);
	}

	public ErroApi(HttpStatus status, String detalhe, HttpHeaders cabecalhos) {
		super(status, ProblemDetail.forStatusAndDetail(status, detalhe), null);
		this.cabecalhos = cabecalhos;
	}

	/** Erro de regra preso a campos do formulário, no mesmo formato da validação ({@code campos}). */
	public static ErroApi campos(Map<String, String> campos) {
		var erro = new ErroApi(HttpStatus.BAD_REQUEST, "Confira os campos destacados.");
		erro.getBody().setTitle("Dados inválidos");
		erro.getBody().setProperty("campos", campos);
		return erro;
	}

	@Override
	public HttpHeaders getHeaders() {
		return cabecalhos;
	}

}
