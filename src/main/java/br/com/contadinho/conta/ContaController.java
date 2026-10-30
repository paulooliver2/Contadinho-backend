package br.com.contadinho.conta;

import java.util.UUID;

import br.com.contadinho.comum.ErroApi;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conta")
class ContaController {

	private final UsuarioRepositorio usuarios;

	ContaController(UsuarioRepositorio usuarios) {
		this.usuarios = usuarios;
	}

	record Conta(@NotNull UUID id, @NotNull String nome, @NotNull String email) {
	}

	@GetMapping
	Conta eu(@AuthenticationPrincipal Jwt jwt) {
		return usuarios.findById(UUID.fromString(jwt.getSubject()))
			.map(u -> new Conta(u.getId(), u.getNome(), u.getEmail()))
			.orElseThrow(() -> new ErroApi(HttpStatus.UNAUTHORIZED, AutenticacaoServico.SESSAO_ENCERRADA));
	}

}
