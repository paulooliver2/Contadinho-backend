package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;

/**
 * De onde vêm os preços de hoje (HU-31): brapi, CoinGecko, Tesouro Transparente e Banco Central, ou a falsa
 * (contadinho.ativos.fonte=falsa). Cada método lança {@link Indisponivel} quando a fonte falha.
 */
public interface FonteCotacoes {

	/** Um ticker que a fonte não conhece fica de fora do mapa. */
	Map<String, BigDecimal> acoes(Collection<String> tickers);

	/** Ids da CoinGecko → preço em reais. */
	Map<String, BigDecimal> criptos(Collection<String> ids);

	/** Nome oficial + " " + ano de vencimento (como o código da HU-30) → preço de venda e a data dele. */
	Map<String, Preco> tesouro();

	/** O CDI de cada dia útil entre as datas (inclusive), em % ao dia. */
	NavigableMap<LocalDate, BigDecimal> cdi(LocalDate de, LocalDate ate);

	record Preco(BigDecimal valor, LocalDate referencia) {
	}

	/** A fonte não respondeu, respondeu erro ou estourou a cota. */
	class Indisponivel extends RuntimeException {

		public Indisponivel(String fonte, Throwable causa) {
			// Sem a URL: ela pode levar a chave (RNF-03).
			super(fonte + " indisponível" + (causa == null ? "" : ": " + causa.getClass().getSimpleName()));
		}

	}

}
