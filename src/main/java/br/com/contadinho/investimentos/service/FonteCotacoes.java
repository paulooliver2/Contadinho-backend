package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
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

	/**
	 * Nome oficial + " " + ano (como o código da HU-30) → os preços de venda, da data mais antiga para a mais nova.
	 * HU-32: só os códigos pedidos; cada lista traz os dias com data base a partir da data pedida e sempre o mais
	 * novo. Um código que a planilha não tem fica de fora.
	 */
	Map<String, List<Preco>> tesouro(Map<String, LocalDate> desde);

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
