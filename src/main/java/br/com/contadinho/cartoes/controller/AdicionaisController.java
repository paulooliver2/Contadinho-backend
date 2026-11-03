package br.com.contadinho.cartoes.controller;

import java.util.List;
import java.util.UUID;

import br.com.contadinho.cartoes.domain.CartaoAdicional;
import br.com.contadinho.cartoes.service.AdicionalServico;
import br.com.contadinho.comum.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cartões adicionais (HU-38): as compras feitas com eles chegam atribuídas à pessoa (RN-19). */
@RestController
@RequestMapping("/api/cartoes/{cartaoId}/adicionais")
public class AdicionaisController {

	private final AdicionalServico servico;

	public AdicionaisController(AdicionalServico servico) {
		this.servico = servico;
	}

	public record AdicionalPedido(
			@NotNull(message = "Informe os 4 últimos dígitos do adicional.")
			@Pattern(regexp = "\\d{4}", message = "Informe os 4 últimos dígitos do adicional.") String ultimos4,
			@NotNull(message = "Escolha de quem é o adicional.") UUID pessoaId) {

		public AdicionalPedido {
			ultimos4 = ultimos4 == null ? null : ultimos4.strip();
		}
	}

	public record AdicionalResposta(@NotNull UUID id, @NotNull String ultimos4, @NotNull UUID pessoaId) {

		static AdicionalResposta de(CartaoAdicional a) {
			return new AdicionalResposta(a.getId(), a.getUltimos4(), a.getPessoaId());
		}
	}

	/**
	 * {@code trazFinal}: se os arquivos já importados neste cartão trazem o final de cada compra. Sem isso, a regra
	 * não se aplica (HU-38.3).
	 */
	public record AdicionaisResposta(@NotNull AdicionalServico.TrazFinal trazFinal,
			@NotNull List<AdicionalResposta> adicionais) {
	}

	@GetMapping
	public AdicionaisResposta listar(@UsuarioLogado UUID usuarioId, @PathVariable UUID cartaoId) {
		var a = servico.listar(usuarioId, cartaoId);
		return new AdicionaisResposta(a.trazFinal(), a.lista().stream().map(AdicionalResposta::de).toList());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AdicionalResposta cadastrar(@UsuarioLogado UUID usuarioId, @PathVariable UUID cartaoId,
			@Valid @RequestBody AdicionalPedido pedido) {
		return AdicionalResposta.de(servico.cadastrar(usuarioId, cartaoId, pedido.ultimos4(), pedido.pessoaId()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remover(@UsuarioLogado UUID usuarioId, @PathVariable UUID cartaoId, @PathVariable UUID id) {
		servico.remover(usuarioId, cartaoId, id);
	}

}
