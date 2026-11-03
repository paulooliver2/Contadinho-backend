package br.com.contadinho.investimentos.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A posição calculada das movimentações, em ordem de data e de lançamento (RNF-05). Aporte recalcula o preço médio;
 * resgate não (RN-48); depois de zerar, o próximo aporte recomeça. Na renda fixa, o aplicado é o valor aplicado menos
 * os resgates (nunca abaixo de 0) e só "Resgatei tudo" encerra.
 *
 * @param quantidade nula na renda fixa
 * @param precoMedio nulo na renda fixa
 * @param aplicado em reais, com centavos
 */
public record Posicao(BigDecimal quantidade, BigDecimal precoMedio, BigDecimal aplicado, boolean encerrada) {

	public static final String NEGATIVA = "A quantidade ficaria negativa. Confira os resgates.";

	public static final String JA_RESGATADA = "Esta aplicação já foi resgatada toda.";

	private static final Comparator<Movimentacao> ORDEM = Comparator.comparing(Movimentacao::getData)
		.thenComparing(Movimentacao::getCriadoEm);

	public static Posicao de(Investimento.Tipo tipo, List<Movimentacao> movimentacoes) {
		return calcular(tipo, movimentacoes).posicao;
	}

	/** O que impede esta sequência: algum passo negativo, ou movimento depois de a renda fixa encerrar. */
	public static Optional<String> problema(Investimento.Tipo tipo, List<Movimentacao> movimentacoes) {
		return Optional.ofNullable(calcular(tipo, movimentacoes).problema);
	}

	private record Calculo(Posicao posicao, String problema) {
	}

	private static Calculo calcular(Investimento.Tipo tipo, List<Movimentacao> movimentacoes) {
		var ordem = movimentacoes.stream().sorted(ORDEM).toList();
		return tipo == Investimento.Tipo.RENDA_FIXA ? rendaFixa(ordem) : porQuantidade(ordem);
	}

	private static Calculo porQuantidade(List<Movimentacao> ordem) {
		var quantidade = BigDecimal.ZERO;
		var precoMedio = BigDecimal.ZERO;
		String problema = null;
		for (var m : ordem) {
			if (m.getTipo() == Movimentacao.Tipo.RESGATE) {
				quantidade = quantidade.subtract(m.getQuantidade());
				if (quantidade.signum() < 0 && problema == null) {
					problema = NEGATIVA;
				}
				continue;
			}
			var preco = m.getPreco() != null ? m.getPreco()
					: m.getValor().divide(m.getQuantidade(), MathContext.DECIMAL128);
			if (quantidade.signum() <= 0) {
				precoMedio = preco;
				quantidade = m.getQuantidade();
			}
			else {
				var nova = quantidade.add(m.getQuantidade());
				precoMedio = quantidade.multiply(precoMedio)
					.add(m.getQuantidade().multiply(preco))
					.divide(nova, MathContext.DECIMAL128);
				quantidade = nova;
			}
		}
		var encerrada = quantidade.signum() <= 0;
		var aplicado = encerrada ? BigDecimal.ZERO.setScale(2)
				: quantidade.multiply(precoMedio).setScale(2, RoundingMode.HALF_UP);
		var posicao = new Posicao(quantidade.max(BigDecimal.ZERO).stripTrailingZeros(),
				precoMedio.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros(), aplicado, encerrada);
		return new Calculo(posicao, problema);
	}

	private static Calculo rendaFixa(List<Movimentacao> ordem) {
		var aplicado = BigDecimal.ZERO;
		var encerrada = false;
		String problema = null;
		for (var m : ordem) {
			if (encerrada && problema == null) {
				problema = JA_RESGATADA;
			}
			if (m.getTipo() == Movimentacao.Tipo.RESGATE) {
				aplicado = aplicado.subtract(m.getValor()).max(BigDecimal.ZERO);
				encerrada = encerrada || m.isTotal();
			}
			else {
				aplicado = aplicado.add(m.getValor());
			}
		}
		return new Calculo(new Posicao(null, null, encerrada ? BigDecimal.ZERO.setScale(2) : aplicado.setScale(2),
				encerrada), problema);
	}

}
