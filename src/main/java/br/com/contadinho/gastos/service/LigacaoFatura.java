package br.com.contadinho.gastos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.com.contadinho.cartoes.Compras;
import br.com.contadinho.gastos.domain.Gasto;
import br.com.contadinho.gastos.domain.OcorrenciaConta;
import br.com.contadinho.gastos.repository.GastoRepositorio;
import br.com.contadinho.gastos.repository.OcorrenciaContaRepositorio;

import org.springframework.stereotype.Component;

/**
 * A regra que liga o que foi pago no cartão ao lançamento da fatura, comum à conta fixa (RN-23, a partir do
 * vencimento) e ao gasto avulso (RN-26, a partir da data do gasto). Um lançamento paga uma coisa só.
 */
@Component
public class LigacaoFatura {

	/** A compra cai em até 5 dias da data, com até R$ 1 de diferença. */
	public static final int DIAS = 5;

	public static final BigDecimal DIFERENCA_DE_VALOR = BigDecimal.ONE;

	private final OcorrenciaContaRepositorio ocorrencias;

	private final GastoRepositorio gastos;

	public LigacaoFatura(OcorrenciaContaRepositorio ocorrencias, GastoRepositorio gastos) {
		this.ocorrencias = ocorrencias;
		this.gastos = gastos;
	}

	/** O que aguarda a fatura: a data de referência e o valor esperado. */
	public record Pendente<T>(T item, LocalDate data, BigDecimal valor) {
	}

	public record Par<T>(T item, Compras.Compra compra) {
	}

	/** Desses lançamentos, os que já pagam uma conta fixa ou um gasto. */
	public Set<UUID> usados(UUID usuarioId, Collection<UUID> lancamentoIds) {
		if (lancamentoIds.isEmpty()) {
			return new HashSet<>();
		}
		return Stream.concat(
				ocorrencias.findByUsuarioIdAndLancamentoIdIn(usuarioId, lancamentoIds).stream()
					.map(OcorrenciaConta::getLancamentoId),
				gastos.findByUsuarioIdAndLancamentoIdIn(usuarioId, lancamentoIds).stream().map(Gasto::getLancamentoId))
			.collect(Collectors.toCollection(HashSet::new));
	}

	/**
	 * Os pares dentro da regra, sem repetir pendente nem compra. Com mais de um par possível, ganham os de data mais
	 * perto e depois os de valor mais perto. As compras de {@code usadas} ficam de fora.
	 */
	public static <T> List<Par<T>> parear(List<Pendente<T>> pendentes, List<Compras.Compra> compras, Set<UUID> usadas) {
		record Candidato<T>(Pendente<T> pendente, Compras.Compra compra, long dias, BigDecimal diferenca) {
		}
		var candidatos = new ArrayList<Candidato<T>>();
		for (var p : pendentes) {
			for (var c : compras) {
				var dias = Math.abs(ChronoUnit.DAYS.between(c.data(), p.data()));
				var diferenca = c.valor().subtract(p.valor()).abs();
				if (c.valor().signum() > 0 && !usadas.contains(c.id()) && dias <= DIAS
						&& diferenca.compareTo(DIFERENCA_DE_VALOR) <= 0) {
					candidatos.add(new Candidato<>(p, c, dias, diferenca));
				}
			}
		}
		candidatos.sort(Comparator.<Candidato<T>>comparingLong(Candidato::dias).thenComparing(Candidato::diferenca));
		var ligados = new HashSet<Pendente<T>>();
		var compraUsada = new HashSet<UUID>();
		var pares = new ArrayList<Par<T>>();
		for (var c : candidatos) {
			if (!ligados.contains(c.pendente()) && compraUsada.add(c.compra().id())) {
				ligados.add(c.pendente());
				pares.add(new Par<>(c.pendente().item(), c.compra()));
			}
		}
		return pares;
	}

}
