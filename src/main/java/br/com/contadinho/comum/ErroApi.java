package br.com.contadinho.comum;

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

	@Override
	public HttpHeaders getHeaders() {
		return cabecalhos;
	}

}
