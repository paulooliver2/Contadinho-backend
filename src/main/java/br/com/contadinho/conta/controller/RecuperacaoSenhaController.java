package br.com.contadinho.conta.controller;

import br.com.contadinho.conta.service.RecuperacaoSenhaServico;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Esqueci a senha (HU-40). Rotas abertas, como o login.
 */
@RestController
@RequestMapping("/api/auth")
@SecurityRequirements
public class RecuperacaoSenhaController {

	private final RecuperacaoSenhaServico servico;

	public RecuperacaoSenhaController(RecuperacaoSenhaServico servico) {
		this.servico = servico;
	}

	public record RecuperarSenhaPedido(
			@NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.") String email) {

		public RecuperarSenhaPedido {
			email = AutenticacaoController.semEspacos(email);
		}
	}

	public record RedefinirSenhaPedido(
			@NotBlank(message = RecuperacaoSenhaServico.LINK_INVALIDO) String token,
			@SenhaForte String novaSenha) {
	}

	/** Responde igual exista a conta ou não (RN-04). */
	@PostMapping("/recuperar-senha")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void recuperar(@Valid @RequestBody RecuperarSenhaPedido pedido) {
		servico.pedir(pedido.email());
	}

	@PostMapping("/redefinir-senha")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void redefinir(@Valid @RequestBody RedefinirSenhaPedido pedido) {
		servico.redefinir(pedido.token(), pedido.novaSenha());
	}

}
