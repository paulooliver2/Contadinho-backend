package br.com.contadinho.conta.controller;

import java.util.UUID;

import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.conta.repository.UsuarioRepositorio;
import br.com.contadinho.conta.service.AutenticacaoServico;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conta")
public class ContaController {

	private final UsuarioRepositorio usuarios;

	public ContaController(UsuarioRepositorio usuarios) {
		this.usuarios = usuarios;
	}

	public record Conta(@NotNull UUID id, @NotNull String nome, @NotNull String email) {
	}

	@GetMapping
	public Conta eu(@UsuarioLogado UUID usuarioId) {
		return usuarios.findById(usuarioId)
			.map(u -> new Conta(u.getId(), u.getNome(), u.getEmail()))
			.orElseThrow(() -> new ErroApi(HttpStatus.UNAUTHORIZED, AutenticacaoServico.SESSAO_ENCERRADA));
	}

}
