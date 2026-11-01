package br.com.contadinho.conta.controller;

import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.conta.domain.ChavePix;
import br.com.contadinho.conta.domain.Usuario;
import br.com.contadinho.conta.service.ContaServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A conta do dono e a chave Pix que vai nas cobranças (HU-03). */
@RestController
@RequestMapping("/api/conta")
public class ContaController {

	private final ContaServico servico;

	public ContaController(ContaServico servico) {
		this.servico = servico;
	}

	/** CPF só com dígitos e celular como +5561999998888; o app formata para mostrar. */
	public record Pix(@NotNull ChavePix.Tipo tipo, @NotNull String chave) {

		public static Pix de(Usuario u) {
			return u.getPixTipo() == null ? null : new Pix(u.getPixTipo(), u.getPixChave());
		}
	}

	public record Conta(
			@NotNull UUID id,
			@NotNull String nome,
			@NotNull String email,
			/** Sem chave, não vem; o app pede antes de cobrar (RN-06). */
			Pix pix) {
	}

	public record PixPedido(
			@NotNull(message = "Escolha o tipo da chave.") ChavePix.Tipo tipo,
			@NotBlank(message = "Informe a chave.")
			@Size(max = 100, message = "Chave longa demais.") String chave) {
	}

	@GetMapping
	public Conta eu(@UsuarioLogado UUID usuarioId) {
		var u = servico.conta(usuarioId);
		return new Conta(u.getId(), u.getNome(), u.getEmail(), Pix.de(u));
	}

	/** HU-03.1. Chave fora do formato do tipo é 400 no campo chave. */
	@PutMapping("/pix")
	public Pix definirPix(@UsuarioLogado UUID usuarioId, @Valid @RequestBody PixPedido pedido) {
		return Pix.de(servico.definirPix(usuarioId, pedido.tipo(), pedido.chave()));
	}

	@DeleteMapping("/pix")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removerPix(@UsuarioLogado UUID usuarioId) {
		servico.removerPix(usuarioId);
	}

}
