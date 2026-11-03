package br.com.contadinho.investimentos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** O CDI diário (série 12 do SGS do Banco Central), em pedaços de até 10 anos, como a API exige. Sem chave. */
public class CotacoesBancoCentral {

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final RestClient http;

	public CotacoesBancoCentral(RestClient.Builder builder) {
		this.http = builder.baseUrl("https://api.bcb.gov.br").build();
	}

	record Ponto(String data, String valor) {
	}

	public NavigableMap<LocalDate, BigDecimal> cdi(LocalDate de, LocalDate ate) {
		var cdi = new TreeMap<LocalDate, BigDecimal>();
		var inicio = de;
		while (!inicio.isAfter(ate)) {
			var fim = inicio.plusYears(10).minusDays(1);
			if (fim.isAfter(ate)) {
				fim = ate;
			}
			buscar(inicio, fim, cdi);
			inicio = fim.plusDays(1);
		}
		return cdi;
	}

	private void buscar(LocalDate de, LocalDate ate, TreeMap<LocalDate, BigDecimal> cdi) {
		try {
			var pontos = http.get()
				.uri("/dados/serie/bcdata.sgs.12/dados?formato=json&dataInicial=" + DIA.format(de) + "&dataFinal="
						+ DIA.format(ate))
				.retrieve()
				.body(Ponto[].class);
			if (pontos == null) {
				return;
			}
			for (var p : pontos) {
				cdi.put(LocalDate.parse(p.data(), DIA), new BigDecimal(p.valor().replace(",", ".")));
			}
		}
		catch (RestClientResponseException e) {
			// Sem dia útil no intervalo (fim de semana, feriado), a série responde 404.
			if (!e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				throw new FonteCotacoes.Indisponivel("banco central", e);
			}
		}
		catch (RestClientException | java.time.format.DateTimeParseException | NumberFormatException e) {
			throw new FonteCotacoes.Indisponivel("banco central", e);
		}
	}

}
