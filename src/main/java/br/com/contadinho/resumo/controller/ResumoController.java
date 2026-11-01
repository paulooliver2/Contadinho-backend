package br.com.contadinho.resumo.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.UsuarioLogado;
import br.com.contadinho.resumo.service.ResumoServico;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** O resumo do mês no Início (HU-23). */
@RestController
@RequestMapping("/api/resumo")
public class ResumoController {

	private final ResumoServico servico;

	public ResumoController(ResumoServico servico) {
		this.servico = servico;
	}

	public record ReceitasDoResumo(@NotNull BigDecimal total, @NotNull BigDecimal recebido,
			/** Ainda não confirmado. */
			@NotNull BigDecimal previsto) {
	}

	/** {@code pago} mais {@code aPagar} é o total; as três origens também somam o total. */
	public record DespesasDoResumo(
			@NotNull BigDecimal total,
			@NotNull BigDecimal pago,
			/** Contas fixas a pagar e faturas que vencem hoje ou depois. */
			@NotNull BigDecimal aPagar,
			/** Minha parte das faturas que vencem no mês. */
			@NotNull BigDecimal faturas,
			/** Contas fixas fora do cartão, pagas pelo valor pago e a pagar pelo previsto (RN-25). */
			@NotNull BigDecimal contasFixas,
			/** Gastos avulsos fora do cartão. */
			@NotNull BigDecimal avulsos) {
	}

	public record FaturaDoResumo(
			@NotNull UUID cartaoId,
			/** Apelido e final, como "Inter •• 9453". */
			@NotNull String cartao,
			@NotNull LocalDate vencimento,
			@NotNull BigDecimal total,
			@NotNull BigDecimal minhaParte) {
	}

	public record ResumoDoMesResposta(
			/** Receitas menos despesas. */
			@NotNull BigDecimal saldo,
			@NotNull ReceitasDoResumo receitas,
			@NotNull DespesasDoResumo despesas,
			/** As faturas que vencem no mês, pelo vencimento. */
			@NotNull List<FaturaDoResumo> faturas) {

		public static ResumoDoMesResposta de(ResumoServico.Resumo r) {
			var fora = r.fora();
			return new ResumoDoMesResposta(r.saldo(),
					new ReceitasDoResumo(r.receitas().total(), r.receitas().recebido(), r.receitas().previsto()),
					new DespesasDoResumo(r.totalDespesas(), r.pago(), r.aPagar(), r.minhaParteDasFaturas(),
							fora.contasPagas().add(fora.contasAPagar()), fora.avulsos()),
					r.faturas().stream()
						.map(f -> new FaturaDoResumo(f.cartaoId(), f.cartao(), f.vencimento(), f.total(), f.minhaParte()))
						.toList());
		}
	}

	/** HU-23: receitas, despesas e saldo do mês (yyyy-MM). Nada é guardado: tudo sai dos lançamentos (RN-39). */
	@GetMapping("/mes/{mes}")
	public ResumoDoMesResposta doMes(@UsuarioLogado UUID usuarioId, @PathVariable YearMonth mes) {
		return ResumoDoMesResposta.de(servico.doMes(usuarioId, mes));
	}

}
