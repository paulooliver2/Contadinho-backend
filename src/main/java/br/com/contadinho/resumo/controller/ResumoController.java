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

	public record ProximaContaResposta(@NotNull UUID ocorrenciaId, @NotNull String descricao,
			@NotNull LocalDate vencimento, @NotNull BigDecimal valor) {
	}

	public record ContasAVencerResposta(
			/** O valor do cadastro de todas as contas fixas ativas, no cartão inclusive. */
			@NotNull BigDecimal fixosPorMes,
			/** Até 3, fora do cartão e ainda a pagar, deste mês e do próximo; as já vencidas primeiro. */
			@NotNull List<ProximaContaResposta> proximas) {
	}

	public record DevedorResposta(@NotNull UUID pessoaId, @NotNull BigDecimal saldo,
			/** Já pagou algo e ainda deve. */
			@NotNull Boolean pagouParte) {
	}

	public record AReceberDoInicioResposta(
			@NotNull BigDecimal total,
			/** O registrado para quem ainda deve, antes dos pagamentos. */
			@NotNull BigDecimal original,
			/** Só quem ainda deve, do vencimento mais próximo para o último. */
			@NotNull List<DevedorResposta> devedores) {
	}

	public record ParteDaFaturaAtual(
			/** Sem pessoa, é do dono. */
			UUID pessoaId,
			@NotNull BigDecimal total) {
	}

	public record FaturaAtualResposta(
			@NotNull UUID cartaoId,
			@NotNull String cartao,
			/** Mês de vencimento, como 2026-11, para abrir a fatura em Cartões. */
			@NotNull String mes,
			@NotNull LocalDate vencimento,
			/** Já fechou: só falta pagar. */
			@NotNull Boolean fechada,
			@NotNull BigDecimal total,
			/** O dono primeiro, depois as pessoas da maior parte para a menor (HU-10). */
			@NotNull List<ParteDaFaturaAtual> partes) {
	}

	public record ProximosResposta(
			@NotNull ContasAVencerResposta contas,
			@NotNull AReceberDoInicioResposta aReceber,
			/** De cada cartão ativo, a próxima fatura a vencer (a que vence hoje ainda conta). */
			@NotNull List<FaturaAtualResposta> faturas) {

		public static ProximosResposta de(ResumoServico.Proximos p) {
			return new ProximosResposta(
					new ContasAVencerResposta(p.contas().fixosPorMes(),
							p.contas().proximas().stream()
								.map(c -> new ProximaContaResposta(c.ocorrenciaId(), c.descricao(), c.vencimento(), c.valor()))
								.toList()),
					new AReceberDoInicioResposta(p.aReceber().total(), p.aReceber().original(),
							p.aReceber().devedores().stream()
								.map(d -> new DevedorResposta(d.pessoaId(), d.saldo(), d.pagouParte()))
								.toList()),
					p.faturas().stream()
						.map(f -> new FaturaAtualResposta(f.cartaoId(), f.cartao(), f.mes().toString(), f.vencimento(),
								f.fechada(), f.total(),
								f.partes().stream().map(x -> new ParteDaFaturaAtual(x.pessoaId(), x.total())).toList()))
						.toList());
		}
	}

	/** HU-24: as próximas contas, quem ainda deve e a fatura atual de cada cartão. Não depende do mês do resumo. */
	@GetMapping("/proximos")
	public ProximosResposta proximos(@UsuarioLogado UUID usuarioId) {
		return ProximosResposta.de(servico.proximos(usuarioId));
	}

	/** HU-23: receitas, despesas e saldo do mês (yyyy-MM). Nada é guardado: tudo sai dos lançamentos (RN-39). */
	@GetMapping("/mes/{mes}")
	public ResumoDoMesResposta doMes(@UsuarioLogado UUID usuarioId, @PathVariable YearMonth mes) {
		return ResumoDoMesResposta.de(servico.doMes(usuarioId, mes));
	}

}
