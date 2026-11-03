package br.com.contadinho.investimentos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** HU-32: quanto da carteira está em cada classe. ETF fica junto com ações; sem exterior por enquanto. */
public final class Distribuicao {

	public enum Classe {
		RENDA_FIXA, ACOES, FIIS, CRIPTO
	}

	public record Fatia(Classe classe, BigDecimal valor, BigDecimal percentual) {
	}

	private static final BigDecimal CEM = BigDecimal.valueOf(100).setScale(2);

	private Distribuicao() {
	}

	public static Classe classe(Investimento i) {
		return switch (i.getTipo()) {
			case TESOURO, RENDA_FIXA -> Classe.RENDA_FIXA;
			case ACAO -> i.isFii() ? Classe.FIIS : Classe.ACOES;
			case CRIPTO -> Classe.CRIPTO;
		};
	}

	/**
	 * Só as classes com valor, na ordem do enum. Os percentuais somam 100,00: a sobra do arredondamento vai para a
	 * maior fatia (no empate, a primeira).
	 */
	public static List<Fatia> de(Map<Classe, BigDecimal> valores) {
		var comValor = new ArrayList<Fatia>();
		var total = BigDecimal.ZERO;
		for (var classe : Classe.values()) {
			var valor = valores.get(classe);
			if (valor != null && valor.signum() > 0) {
				comValor.add(new Fatia(classe, valor, null));
				total = total.add(valor);
			}
		}
		if (comValor.isEmpty()) {
			return List.of();
		}
		var fatias = new ArrayList<Fatia>();
		var soma = BigDecimal.ZERO;
		for (var f : comValor) {
			var percentual = f.valor().multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
			fatias.add(new Fatia(f.classe(), f.valor(), percentual));
			soma = soma.add(percentual);
		}
		var maior = fatias.stream().max(Comparator.comparing(Fatia::valor)).orElseThrow();
		for (var f : fatias) {
			if (f.valor().compareTo(maior.valor()) == 0) {
				maior = f;
				break;
			}
		}
		var indice = fatias.indexOf(maior);
		fatias.set(indice, new Fatia(maior.classe(), maior.valor(), maior.percentual().add(CEM.subtract(soma))));
		return List.copyOf(fatias);
	}

}
