package br.com.contadinho.investimentos.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * O preço de venda do Tesouro Direto na planilha de dados abertos do Tesouro Transparente. O arquivo tem o histórico
 * inteiro; é lido linha a linha e fica só a data mais recente de cada título.
 */
public class CotacoesTesouro {

	public static final String ENDERECO = "https://www.tesourotransparente.gov.br/ckan/dataset/"
			+ "df56aa42-484a-4a59-8184-7676580c81e3/resource/796d2059-14e9-44e3-80c9-2d9e30b405c1/download/"
			+ "PrecoTaxaTesouroDireto.csv";

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final RestClient http;

	public CotacoesTesouro(RestClient.Builder builder) {
		this.http = builder.build();
	}

	private record Linha(LocalDate vencimento, LocalDate base, BigDecimal preco) {
	}

	public Map<String, FonteCotacoes.Preco> tesouro() {
		try {
			Map<String, Linha> titulos = http.get().uri(ENDERECO).exchange((pedido, resposta) -> {
				if (!resposta.getStatusCode().is2xxSuccessful()) {
					throw new FonteCotacoes.Indisponivel("tesouro", null);
				}
				return ler(new BufferedReader(new InputStreamReader(resposta.getBody(), StandardCharsets.ISO_8859_1)));
			});
			var precos = new HashMap<String, FonteCotacoes.Preco>();
			titulos.forEach((codigo, l) -> precos.put(codigo, new FonteCotacoes.Preco(l.preco(), l.base())));
			return precos;
		}
		catch (RestClientException e) {
			throw new FonteCotacoes.Indisponivel("tesouro", e);
		}
	}

	private static Map<String, Linha> ler(BufferedReader leitor) throws IOException {
		var cabecalho = leitor.readLine();
		if (cabecalho == null) {
			throw new FonteCotacoes.Indisponivel("tesouro", null);
		}
		// Tira o BOM e qualquer coisa antes da primeira letra.
		List<String> colunas = Arrays.asList(cabecalho.replaceFirst("^[^A-Za-z]+", "").split(";"));
		int tipo = colunas.indexOf("Tipo Titulo");
		int vencimento = colunas.indexOf("Data Vencimento");
		int base = colunas.indexOf("Data Base");
		int preco = colunas.indexOf("PU Venda Manha");
		if (tipo < 0 || vencimento < 0 || base < 0 || preco < 0) {
			throw new FonteCotacoes.Indisponivel("tesouro", null);
		}
		int minimo = Math.max(Math.max(tipo, vencimento), Math.max(base, preco));
		var titulos = new HashMap<String, Linha>();
		String texto;
		while ((texto = leitor.readLine()) != null) {
			var campos = texto.split(";", -1);
			if (campos.length <= minimo) {
				continue;
			}
			try {
				var linha = new Linha(LocalDate.parse(campos[vencimento].trim(), DIA),
						LocalDate.parse(campos[base].trim(), DIA),
						new BigDecimal(campos[preco].trim().replace(".", "").replace(",", ".")));
				var nome = campos[tipo].trim();
				var codigo = nome + " " + anoDoNome(nome, linha.vencimento());
				titulos.merge(codigo, linha, CotacoesTesouro::maisNova);
			}
			catch (DateTimeParseException | NumberFormatException e) {
				// Linha malformada: fica de fora.
			}
		}
		return titulos;
	}

	/**
	 * O ano do nome do título. Educa+ e Renda+ trazem na planilha o último pagamento: o Educa+ paga por 5 anos
	 * (2030 vence em 2034) e o Renda+ por 20 (2030 vence em 2049).
	 */
	private static int anoDoNome(String nome, LocalDate vencimento) {
		if (nome.startsWith("Tesouro Educa+")) {
			return vencimento.getYear() - 4;
		}
		if (nome.startsWith("Tesouro Renda+")) {
			return vencimento.getYear() - 19;
		}
		return vencimento.getYear();
	}

	/** A data base mais recente; no mesmo dia, dois vencimentos no mesmo ano ficam com o mais próximo. */
	private static Linha maisNova(Linha a, Linha b) {
		if (!a.base().equals(b.base())) {
			return a.base().isAfter(b.base()) ? a : b;
		}
		return a.vencimento().isBefore(b.vencimento()) ? a : b;
	}

}
