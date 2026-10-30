package br.com.contadinho.comum;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

}
