package br.com.contadinho.conta;

import java.time.Instant;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas abertas, sem token de acesso (RNF-01).
 */
@RestController
@RequestMapping("/api/auth")
@SecurityRequirements
class AutenticacaoController {

	private final AutenticacaoServico servico;

	AutenticacaoController(AutenticacaoServico servico) {
		this.servico = servico;
	}

	/** RN-02: pelo menos 10 caracteres, com letras e números. O bcrypt só considera os primeiros 72 bytes. */
	record CadastroPedido(
			@NotBlank(message = "Diga como quer ser chamado.") @Size(max = 80, message = "Use até 80 caracteres.") String nome,
			@NotBlank(message = "Informe o e-mail.") @Email(message = "Este e-mail não parece válido.") @Size(max = 254, message = "E-mail longo demais.") String email,
			@NotNull(message = "Crie uma senha.")
			@Size(min = 10, max = 64, message = "A senha precisa ter de 10 a 64 caracteres.")
			@Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "A senha precisa ter letras e números.") String senha) {

		/** Espaços que o teclado do celular deixa no começo ou no fim não contam; a senha fica como veio. */
		CadastroPedido {
			nome = semEspacos(nome);
			email = semEspacos(email);
		}
	}

	record EntrarPedido(
			@NotBlank(message = "Informe o e-mail.") String email,
			@NotBlank(message = "Informe a senha.") @Size(max = 64, message = AutenticacaoServico.CREDENCIAIS_INVALIDAS) String senha) {

		EntrarPedido {
			email = semEspacos(email);
		}
	}

	private static String semEspacos(String texto) {
		return texto == null ? null : texto.strip();
	}

	record SessaoPedido(@NotBlank String refreshToken) {
	}

	record Tokens(
			@NotNull String tokenAcesso,
			@NotNull Instant acessoExpiraEm,
			@NotNull String refreshToken) {

		static Tokens de(AutenticacaoServico.Tokens t) {
			return new Tokens(t.tokenAcesso(), t.acessoExpiraEm(), t.refreshToken());
		}
	}

	@PostMapping("/cadastro")
	@ResponseStatus(HttpStatus.CREATED)
	Tokens cadastrar(@Valid @RequestBody CadastroPedido pedido, HttpServletRequest requisicao) {
		return Tokens.de(servico.cadastrar(pedido.nome(), pedido.email(), pedido.senha(), dispositivo(requisicao)));
	}

	@PostMapping("/entrar")
	Tokens entrar(@Valid @RequestBody EntrarPedido pedido, HttpServletRequest requisicao) {
		return Tokens.de(servico.entrar(pedido.email(), pedido.senha(), dispositivo(requisicao)));
	}

	@PostMapping("/renovar")
	Tokens renovar(@Valid @RequestBody SessaoPedido pedido) {
		return Tokens.de(servico.renovar(pedido.refreshToken()));
	}

	@PostMapping("/sair")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void sair(@Valid @RequestBody SessaoPedido pedido) {
		servico.sair(pedido.refreshToken());
	}

	/** Só para a lista de sessões abertas que vem depois; cabe na coluna dispositivo. */
	private static String dispositivo(HttpServletRequest requisicao) {
		var agente = requisicao.getHeader(HttpHeaders.USER_AGENT);
		return agente == null ? null : agente.substring(0, Math.min(agente.length(), 120));
	}

}
