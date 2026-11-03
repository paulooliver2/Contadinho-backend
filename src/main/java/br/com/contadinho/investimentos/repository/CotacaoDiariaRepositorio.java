package br.com.contadinho.investimentos.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import br.com.contadinho.investimentos.domain.Investimento;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** HU-32: os preços de cada dia, lidos de uma vez para todos os dias do período. */
@Repository
public class CotacaoDiariaRepositorio {

	private final NamedParameterJdbcTemplate jdbc;

	public CotacaoDiariaRepositorio(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Todas as linhas com data até {@code ate}, por código; códigos sem linha não aparecem. */
	public Map<String, NavigableMap<LocalDate, BigDecimal>> porCodigo(Investimento.Tipo tipo,
			Collection<String> codigos, LocalDate ate) {
		var resultado = new HashMap<String, NavigableMap<LocalDate, BigDecimal>>();
		if (codigos.isEmpty()) {
			return resultado;
		}
		var parametros = new MapSqlParameterSource().addValue("tipo", tipo.name())
			.addValue("codigos", codigos)
			.addValue("ate", Date.valueOf(ate));
		jdbc.query("""
				SELECT codigo, data, preco FROM cotacao_diaria
				WHERE tipo = :tipo AND codigo IN (:codigos) AND data <= :ate
				""", parametros, linha -> {
			resultado.computeIfAbsent(linha.getString("codigo"), c -> new TreeMap<>())
				.put(linha.getDate("data").toLocalDate(), linha.getBigDecimal("preco"));
		});
		return resultado;
	}

}
