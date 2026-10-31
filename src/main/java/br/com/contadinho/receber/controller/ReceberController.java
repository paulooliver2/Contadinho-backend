package br.com.contadinho.receber.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.cartoes.Compras;
import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.receber.service.ReceberServico;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A receber: quanto cada pessoa deve e o histórico dela (HU-18).
 */
@RestController
@RequestMapping("/api/receber")
public class ReceberController {

	private final ReceberServico servico;

	public ReceberController(ReceberServico servico) {
		this.servico = servico;
	}

	/** RN-29. Saldo = original − recebido. */
	public record SituacaoResposta(
			@NotNull UUID pessoaId,
			@NotNull BigDecimal saldo,
			@NotNull BigDecimal original,
			@NotNull BigDecimal recebido,
			@NotNull ReceberServico.Status status,
			/** Do recebível em aberto que vence primeiro; sem nada em aberto, não vem. */
			LocalDate proximoVencimento) {

		public static SituacaoResposta de(ReceberServico.Situacao s) {
			return new SituacaoResposta(s.pessoaId(), s.saldo(), s.original(), s.recebido(), s.status(),
					s.proximoVencimento());
		}
	}

	public record ResumoResposta(
			@NotNull BigDecimal aReceber,
			@NotNull BigDecimal recebido,
			/** Só quem tem algo registrado, pelo vencimento mais próximo (HU-18.2). */
			@NotNull List<SituacaoResposta> pessoas) {
	}

	public record CompraResposta(
			@NotNull UUID lancamentoId,
			@NotNull LocalDate data,
			@NotNull String descricao,
			@NotNull BigDecimal valor,
			Integer parcelaAtual,
			Integer parcelaTotal,
			/** Apelido e final, como "Itaú •• 3310". */
			@NotNull String cartao,
			/** Mês de vencimento da fatura, como 2026-11. */
			@NotNull String fatura) {

		public static CompraResposta de(Compras.Compra c) {
			return new CompraResposta(c.id(), c.data(), c.descricao(), c.valor(), c.parcelaAtual(), c.parcelaTotal(),
					c.cartao(), c.fatura().toString());
		}
	}

	public record HistoricoResposta(@NotNull SituacaoResposta situacao, @NotNull List<CompraResposta> compras) {
	}

	@GetMapping
	public ResumoResposta resumo(@UsuarioLogado UUID usuarioId) {
		var resumo = servico.resumo(usuarioId);
		return new ResumoResposta(resumo.aReceber(), resumo.recebido(),
				resumo.pessoas().stream().map(SituacaoResposta::de).toList());
	}

	@GetMapping("/pessoas/{pessoaId}")
	public HistoricoResposta historico(@UsuarioLogado UUID usuarioId, @PathVariable UUID pessoaId) {
		var historico = servico.historico(usuarioId, pessoaId);
		return new HistoricoResposta(SituacaoResposta.de(historico.situacao()),
				historico.compras().stream().map(CompraResposta::de).toList());
	}

}
