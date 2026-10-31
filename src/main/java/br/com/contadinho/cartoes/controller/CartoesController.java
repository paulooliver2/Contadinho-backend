package br.com.contadinho.cartoes.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.cartoes.domain.Banco;
import br.com.contadinho.cartoes.domain.Cartao;
import br.com.contadinho.cartoes.repository.CartaoRepositorio;
import br.com.contadinho.comum.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro e lista de cartões (HU-05). As faturas ficam no {@link FaturasController}.
 */
@RestController
@RequestMapping("/api/cartoes")
public class CartoesController {

	private final CartaoRepositorio cartoes;

	private final Clock clock;

	public CartoesController(CartaoRepositorio cartoes, Clock clock) {
		this.cartoes = cartoes;
		this.clock = clock;
	}

	public record BancoResposta(@NotNull Banco codigo, @NotNull String nome, @NotNull Banco.Modelo modelo) {
	}

	/** RN-07 e RN-08. Sem apelido, o cartão leva o nome do banco. */
	public record NovoCartaoPedido(
			@Size(max = 40, message = "Use até 40 caracteres.") String apelido,
			@NotNull(message = "Escolha o banco.") Banco banco,
			@NotNull(message = "Informe os 4 últimos dígitos do cartão.")
			@Pattern(regexp = "\\d{4}", message = "Informe os 4 últimos dígitos do cartão.") String ultimos4,
			@NotNull(message = "Informe o dia do fechamento.")
			@Min(value = 1, message = "Use um dia entre 1 e 31.") @Max(value = 31, message = "Use um dia entre 1 e 31.") Integer diaFechamento,
			@NotNull(message = "Informe o dia do vencimento.")
			@Min(value = 1, message = "Use um dia entre 1 e 31.") @Max(value = 31, message = "Use um dia entre 1 e 31.") Integer diaVencimento) {

		public NovoCartaoPedido {
			apelido = apelido == null ? null : apelido.strip();
			ultimos4 = ultimos4 == null ? null : ultimos4.strip();
		}
	}

	public record CartaoResposta(
			@NotNull UUID id,
			@NotNull String apelido,
			@NotNull Banco banco,
			@NotNull String bancoNome,
			@NotNull String ultimos4,
			@NotNull Integer diaFechamento,
			@NotNull Integer diaVencimento,
			/** Mês de vencimento da fatura aberta, como 2026-11 (RN-58). */
			@NotNull String faturaAberta) {

		public static CartaoResposta de(Cartao c, LocalDate hoje) {
			return new CartaoResposta(c.getId(), c.getApelido(), c.getBanco(), c.getBanco().nome(), c.getUltimos4(),
					c.getDiaFechamento(), c.getDiaVencimento(), c.mesAberto(hoje).toString());
		}
	}

	@GetMapping("/bancos")
	public List<BancoResposta> bancos() {
		return Arrays.stream(Banco.values()).map(b -> new BancoResposta(b, b.nome(), b.modelo())).toList();
	}

	@GetMapping
	public List<CartaoResposta> listar(@UsuarioLogado UUID usuarioId) {
		return cartoes.findByUsuarioIdAndEncerradoEmIsNullOrderByCriadoEm(usuarioId).stream()
			.map(c -> CartaoResposta.de(c, LocalDate.now(clock)))
			.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CartaoResposta cadastrar(@UsuarioLogado UUID usuarioId, @Valid @RequestBody NovoCartaoPedido pedido) {
		var apelido = pedido.apelido() == null || pedido.apelido().isEmpty()
				? (pedido.banco() == Banco.OUTRO ? "Cartão" : pedido.banco().nome())
				: pedido.apelido();
		var cartao = new Cartao(usuarioId, apelido, pedido.banco(), pedido.ultimos4(), pedido.diaFechamento(),
				pedido.diaVencimento(), Instant.now(clock));
		return CartaoResposta.de(cartoes.save(cartao), LocalDate.now(clock));
	}

}
