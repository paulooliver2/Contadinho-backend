package br.com.contadinho.comum;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todos os erros saem como ProblemDetail. Erros de validação trazem {@code campos}: o nome do campo e a
 * mensagem que o app mostra embaixo dele.
 */
@RestControllerAdvice
class TratamentoErros extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> campos = new LinkedHashMap<>();
		ex.getFieldErrors().forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
		var problema = ex.getBody();
		problema.setTitle("Dados inválidos");
		problema.setDetail("Confira os campos destacados.");
		problema.setProperty("campos", campos);
		return handleExceptionInternal(ex, problema, headers, status, request);
	}

	/** JSON quebrado ou valor fora da lista (ex.: um banco que não existe). */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problema = ProblemDetail.forStatusAndDetail(status, "Não deu para entender os dados enviados. Confira e tente de novo.");
		problema.setTitle("Dados inválidos");
		return handleExceptionInternal(ex, problema, headers, status, request);
	}

	/**
	 * HU-04: a conta foi apagada em outro aparelho e este ainda tem um token de acesso válido (até 15 min). Gravar
	 * esbarra na FK de usuario_id: responde 401 de sessão encerrada, e o app volta ao login. Outras violações seguem
	 * como erro do servidor.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail contaInexistente(DataIntegrityViolationException ex) {
		var mensagem = ex.getMostSpecificCause().getMessage();
		if (mensagem == null || !mensagem.contains("usuario_id_fkey")) {
			throw ex;
		}
		var problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Sua sessão terminou. Entre de novo.");
		problema.setTitle("Sessão encerrada");
		return problema;
	}

	/** Parâmetro do endereço que não é do tipo esperado, como um mês 2026-13 ou um id que não é UUID. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		var problema = ProblemDetail.forStatusAndDetail(status, "Endereço inválido. Volte e tente de novo.");
		problema.setTitle("Dados inválidos");
		return handleExceptionInternal(ex, problema, headers, status, request);
	}

}
