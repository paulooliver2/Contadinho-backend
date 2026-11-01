package br.com.contadinho.gastos.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import br.com.contadinho.gastos.Despesas;
import br.com.contadinho.gastos.domain.OcorrenciaConta;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Despesas} para os outros módulos (RN-38). */
@Service
public class DespesasServico implements Despesas {

	private final ContaFixaServico contas;

	private final GastoServico gastos;

	public DespesasServico(ContaFixaServico contas, GastoServico gastos) {
		this.contas = contas;
		this.gastos = gastos;
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

}
