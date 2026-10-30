package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O que um leitor (OFX, PDF) tira do arquivo da fatura, já com as regras do app.
 *
 * @param formato OFX ou PDF
 * @param modelo leitor usado, como OFX_GENERICO ou PDF_INTER (RN-13)
 * @param mes mês de vencimento informado no arquivo, ou null quando ele não informa (RN-09)
 * @param ignorados pagamentos da fatura anterior, que não são compras (RN-61)
 * @param totalArquivo total da fatura informado pelo banco, quando o arquivo traz (RN-12)
 */
record ArquivoFatura(String formato, String modelo, YearMonth mes, List<Item> itens, int ignorados,
		BigDecimal totalArquivo) {

	static final String SEM_COMPRAS = "O arquivo não tem compras para importar.";

	/**
	 * Uma compra, parcela ou estorno. Valor positivo é compra; negativo, estorno (RN-20). {@code finalCartao} são
	 * os 4 últimos dígitos do cartão que fez a compra, quando o arquivo traz (RN-19).
	 */
	record Item(String idExterno, LocalDate data, String descricao, BigDecimal valor, Integer parcelaAtual,
			Integer parcelaTotal, String finalCartao) {
	}

	static final Pattern PARCELA_ESCRITA = Pattern.compile("(?i)parc(?:ela)?\\.?\\s*(\\d{1,2})\\s*(?:/|de)\\s*(\\d{1,2})");

	static final Pattern PARCELA_NO_FIM = Pattern.compile("(?<!\\d)(\\d{1,2})/(\\d{1,2})\\s*$");

	/** "PARCELA 3/10", "(Parcela 03 de 10)" ou "LOJA X 03/10" no fim da descrição (RN-61). */
	static int[] parcela(String descricao) {
		var escrita = PARCELA_ESCRITA.matcher(descricao);
		if (escrita.find()) {
			return parcelaValida(escrita, 1);
		}
		var noFim = PARCELA_NO_FIM.matcher(descricao);
		return noFim.find() ? parcelaValida(noFim, 2) : null;
	}

	private static int[] parcelaValida(Matcher m, int totalMinimo) {
		int atual = Integer.parseInt(m.group(1));
		int total = Integer.parseInt(m.group(2));
		return atual >= 1 && atual <= total && total >= totalMinimo ? new int[] { atual, total } : null;
	}

	/** Junta os lançamentos lidos aplicando as regras comuns a todos os formatos. */
	static final class Montador {

		private final List<Item> itens = new ArrayList<>();

		private final Map<String, Integer> vistos = new HashMap<>();

		private int ignorados;

		/**
		 * @param idBanco id que o banco dá ao lançamento (FITID); null quando o formato não tem
		 * @param valor positivo para compra, negativo para estorno ou pagamento
		 * @param pagamento o banco marcou como pagamento da fatura
		 */
		void adicionar(String idBanco, LocalDate data, String descricao, BigDecimal valor, boolean pagamento,
				String finalCartao) {
			// RN-61: o pagamento da fatura anterior não é compra.
			if (valor.signum() == 0 || pagamento
					|| (valor.signum() < 0 && Descricoes.normalizar(descricao).startsWith("PAGAMENTO"))) {
				ignorados++;
				return;
			}
			var limpa = descricao.strip().replaceAll("\\s+", " ");
			if (limpa.length() > 255) {
				limpa = limpa.substring(0, 255);
			}
			var parcela = parcela(limpa);
			Integer atual = parcela == null ? null : parcela[0];
			Integer total = parcela == null ? null : parcela[1];
			var id = idExterno(idBanco, data, limpa, valor, atual, total, finalCartao);
			// Duas compras iguais no mesmo dia, ou um FITID repetido: cada lançamento precisa do seu id (RN-11).
			int repeticao = vistos.merge(id, 1, Integer::sum);
			if (repeticao > 1) {
				id = id + "#" + repeticao;
			}
			itens.add(new Item(id, data, limpa, valor, atual, total, finalCartao));
		}

		ArquivoFatura montar(String formato, String modelo, YearMonth mes, BigDecimal totalArquivo)
				throws ArquivoInvalido {
			if (itens.isEmpty()) {
				throw new ArquivoInvalido(SEM_COMPRAS);
			}
			return new ArquivoFatura(formato, modelo, mes, List.copyOf(itens), ignorados, totalArquivo);
		}

		/** RN-11: o id do banco; sem ele (ou comprido demais), um hash do lançamento. */
		private static String idExterno(String idBanco, LocalDate data, String descricao, BigDecimal valor,
				Integer atual, Integer total, String finalCartao) {
			if (idBanco != null && !idBanco.isBlank() && idBanco.length() <= 120) {
				return idBanco;
			}
			var base = data + "|" + Descricoes.normalizar(descricao) + "|" + valor.toPlainString() + "|" + atual
					+ "/" + total + "|" + finalCartao + "|" + (idBanco == null ? "" : idBanco);
			try {
				var hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
				return "h:" + HexFormat.of().formatHex(hash, 0, 24);
			}
			catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
		}

	}

}
