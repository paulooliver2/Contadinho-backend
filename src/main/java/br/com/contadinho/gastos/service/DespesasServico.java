package br.com.contadinho.gastos.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import br.com.contadinho.gastos.Despesas;
import br.com.contadinho.gastos.domain.ContaFixa;
import br.com.contadinho.gastos.domain.OcorrenciaConta;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Despesas} para os outros módulos (RN-38). */
@Service
public class DespesasServico implements Despesas {

	private final ContaFixaServico contas;

	private final GastoServico gastos;

	private final Clock clock;

	public DespesasServico(ContaFixaServico contas, GastoServico gastos, Clock clock) {
		this.contas = contas;
		this.gastos = gastos;
		this.clock = clock;
	}

	@Override
	@Transactional
	public DespesasDoMes doMes(UUID usuarioId, YearMonth mes) {
		var pagas = BigDecimal.ZERO;
		var aPagar = BigDecimal.ZERO;
		for (var c : contas.doMes(usuarioId, mes)) {
			if (c.status() == OcorrenciaConta.Status.PAGA) {
				pagas = pagas.add(c.valor());
			}
			else if (c.status() == OcorrenciaConta.Status.A_PAGAR) {
				aPagar = aPagar.add(c.valor());
			}
		}
		var avulsos = gastos.doMes(usuarioId, mes).stream()
			.filter(g -> !g.gasto().isNoCartao())
			.map(GastoServico.GastoDoMes::valor)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new DespesasDoMes(pagas, aPagar, avulsos);
	}

	@Override
	@Transactional
	public ContasAVencer aVencer(UUID usuarioId, int quantas) {
		var atual = YearMonth.from(LocalDate.now(clock));
		var proximoMes = contas.doMes(usuarioId, atual.plusMonths(1));
		// Toda conta ativa tem ocorrência no próximo mês, inclusive a cadastrada com o dia deste mês já passado.
		var fixosPorMes = proximoMes.stream().map(ContaFixaServico.ContaDoMes::valorAtual).reduce(BigDecimal.ZERO,
				BigDecimal::add);
		var proximas = Stream.concat(contas.doMes(usuarioId, atual).stream(), proximoMes.stream())
			.filter(c -> c.status() == OcorrenciaConta.Status.A_PAGAR)
			.sorted(Comparator.comparing((ContaFixaServico.ContaDoMes c) -> c.ocorrencia().getVencimento())
				.thenComparing(c -> c.conta().getDescricao()))
			.limit(quantas)
			.map(c -> new ContaAVencer(c.ocorrencia().getId(), c.conta().getDescricao(), c.ocorrencia().getVencimento(),
					c.valor()))
			.toList();
		return new ContasAVencer(fixosPorMes, proximas);
	}

	@Override
	@Transactional
	public List<ContaParaAvisar> aPagarAte(UUID usuarioId, LocalDate ate) {
		var hoje = LocalDate.now(clock);
		var lista = new ArrayList<ContaParaAvisar>();
		for (var mes = YearMonth.from(hoje); !mes.isAfter(YearMonth.from(ate)); mes = mes.plusMonths(1)) {
			for (var c : contas.doMes(usuarioId, mes)) {
				var vencimento = c.ocorrencia().getVencimento();
				if (c.status() == OcorrenciaConta.Status.A_PAGAR && !vencimento.isBefore(hoje) && !vencimento.isAfter(ate)) {
					lista.add(new ContaParaAvisar(c.ocorrencia().getId(), c.conta().getDescricao(), vencimento, c.valor(),
							c.conta().getFormaPagamento() == ContaFixa.FormaPagamento.DEBITO_AUTOMATICO));
				}
			}
		}
		return lista;
	}

}
