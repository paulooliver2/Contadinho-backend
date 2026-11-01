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
import br.com.contadinho.cartoes.service.CartaoServico;
import br.com.contadinho.comum.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro, lista, edição, encerramento e exclusão de cartões (HU-05, HU-45). As faturas ficam no {@link FaturasController}.
 */
@RestController
@RequestMapping("/api/cartoes")
public class CartoesController {

	private final CartaoRepositorio cartoes;

	private final CartaoServico servico;

	private final Clock clock;

	public CartoesController(CartaoRepositorio cartoes, CartaoServico servico, Clock clock) {
		this.cartoes = cartoes;
		this.servico = servico;
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

	/** HU-45.1: banco e final do cartão não mudam (o final identifica o cartão na importação). Sem apelido, o nome do banco. */
	public record EdicaoCartaoPedido(
			@Size(max = 40, message = "Use até 40 caracteres.") String apelido,
			@NotNull(message = "Informe o dia do fechamento.")
			@Min(value = 1, message = "Use um dia entre 1 e 31.") @Max(value = 31, message = "Use um dia entre 1 e 31.") Integer diaFechamento,
			@NotNull(message = "Informe o dia do vencimento.")
			@Min(value = 1, message = "Use um dia entre 1 e 31.") @Max(value = 31, message = "Use um dia entre 1 e 31.") Integer diaVencimento) {

		public EdicaoCartaoPedido {
			apelido = apelido == null ? null : apelido.strip();
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
			@NotNull String faturaAberta,
			/** HU-45: sem faturas, contas ou gastos, exclui; senão, só encerra. */
			@NotNull Boolean podeExcluir) {

		static CartaoResposta de(Cartao c, LocalDate hoje, boolean podeExcluir) {
			return new CartaoResposta(c.getId(), c.getApelido(), c.getBanco(), c.getBanco().nome(), c.getUltimos4(),
					c.getDiaFechamento(), c.getDiaVencimento(), c.mesAberto(hoje).toString(), podeExcluir);
		}
	}

	private CartaoResposta resposta(UUID usuarioId, Cartao c) {
		return CartaoResposta.de(c, LocalDate.now(clock), servico.podeExcluir(usuarioId, c.getId()));
	}

	private static String apelido(String apelido, Banco banco) {
		if (apelido != null && !apelido.isEmpty()) {
			return apelido;
		}
		return banco == Banco.OUTRO ? "Cartão" : banco.nome();
	}

	@GetMapping("/bancos")
	public List<BancoResposta> bancos() {
		return Arrays.stream(Banco.values()).map(b -> new BancoResposta(b, b.nome(), b.modelo())).toList();
	}

	@GetMapping
	public List<CartaoResposta> listar(@UsuarioLogado UUID usuarioId) {
		return cartoes.findByUsuarioIdAndEncerradoEmIsNullOrderByCriadoEm(usuarioId).stream()
			.map(c -> resposta(usuarioId, c))
			.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CartaoResposta cadastrar(@UsuarioLogado UUID usuarioId, @Valid @RequestBody NovoCartaoPedido pedido) {
		var cartao = new Cartao(usuarioId, apelido(pedido.apelido(), pedido.banco()), pedido.banco(), pedido.ultimos4(),
				pedido.diaFechamento(), pedido.diaVencimento(), Instant.now(clock));
		return resposta(usuarioId, cartoes.save(cartao));
	}

	/** HU-45.1: os dias novos valem para as faturas ainda não gravadas (RN-55). */
	@PutMapping("/{id}")
	public CartaoResposta alterar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id,
			@Valid @RequestBody EdicaoCartaoPedido pedido) {
		var cartao = servico.alterar(usuarioId, id, c -> apelido(pedido.apelido(), c.getBanco()), pedido.diaFechamento(),
				pedido.diaVencimento());
		return resposta(usuarioId, cartao);
	}

	/** HU-45.3: some da tela Cartões e das listas de escolha; o histórico continua. */
	@PostMapping("/{id}/encerramento")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void encerrar(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.encerrar(usuarioId, id);
	}

	/** HU-45.2: só sem histórico; com ele, 409. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void excluir(@UsuarioLogado UUID usuarioId, @PathVariable UUID id) {
		servico.excluir(usuarioId, id);
	}

}
