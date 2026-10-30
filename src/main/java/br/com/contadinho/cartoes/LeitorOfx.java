package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor OFX genérico (HU-06, HU-37): aceita o OFX 1 (SGML, tags sem fechamento) e o OFX 2 (XML).
 * Não depende do banco; os modelos por banco entram depois, por cima dele.
 */
final class LeitorOfx {

	/** O arquivo não é um OFX que dê para ler. A mensagem vai para o usuário. */
	static final class ArquivoInvalido extends Exception {

		ArquivoInvalido(String mensagem) {
			super(mensagem);
		}

	}

	/** Uma compra, parcela ou estorno do arquivo. Valor positivo é compra; negativo, estorno (RN-20). */
	record Item(String idExterno, LocalDate data, String descricao, BigDecimal valor, Integer parcelaAtual,
			Integer parcelaTotal) {
	}

	/** {@code totalArquivo} é o saldo informado pelo banco (RN-12), quando o arquivo traz. */
	record Arquivo(List<Item> itens, int ignorados, BigDecimal totalArquivo) {
	}

	static final String NAO_E_OFX = "Não reconheci este arquivo como OFX. Exporte a fatura em OFX no app do banco e tente de novo.";

	static final String E_PDF = "PDF não é aceito. Exporte a fatura em OFX no app do banco.";

	static final String E_CSV = "A importação de CSV ainda não está disponível. Exporte a fatura em OFX no app do banco.";

	static final String SEM_COMPRAS = "O arquivo não tem compras para importar.";

	private static final Pattern TRANSACAO = Pattern.compile("(?is)<STMTTRN>(.*?)(?=</STMTTRN>|<STMTTRN>|</BANKTRANLIST>|$)");

	private static final Pattern SALDO = Pattern.compile("(?is)<LEDGERBAL>(.*?)(?=</LEDGERBAL>|<AVAILBAL>|$)");

	private static final Pattern PARCELA_ESCRITA = Pattern.compile("(?i)parc(?:ela)?\\.?\\s*(\\d{1,2})\\s*(?:/|de)\\s*(\\d{1,2})");

	private static final Pattern PARCELA_NO_FIM = Pattern.compile("(?<!\\d)(\\d{1,2})/(\\d{1,2})\\s*$");

	private static final DateTimeFormatter DATA = DateTimeFormatter.BASIC_ISO_DATE;

	private LeitorOfx() {
	}

	/** Recusa PDF e CSV com a orientação certa antes de tentar ler (RN-10). */
	static Arquivo ler(String nomeArquivo, byte[] conteudo) throws ArquivoInvalido {
		var texto = decodificar(conteudo);
		if (texto.startsWith("%PDF")) {
			throw new ArquivoInvalido(E_PDF);
		}
		if (!texto.toUpperCase().contains("<OFX>")) {
			throw new ArquivoInvalido(nomeArquivo.toLowerCase().endsWith(".csv") ? E_CSV : NAO_E_OFX);
		}

		var itens = new ArrayList<Item>();
		var vistos = new HashMap<String, Integer>();
		int ignorados = 0;
		Matcher transacoes = TRANSACAO.matcher(texto);
		while (transacoes.find()) {
			var bloco = transacoes.group(1);
			var item = item(bloco, vistos);
			if (item == null) {
				ignorados++;
			}
			else {
				itens.add(item);
			}
		}
		if (itens.isEmpty()) {
			throw new ArquivoInvalido(SEM_COMPRAS);
		}
		return new Arquivo(itens, ignorados, saldo(texto));
	}

	/** Null quando a transação é o pagamento da fatura, que não é compra (RN-61). */
	private static Item item(String bloco, Map<String, Integer> vistos) throws ArquivoInvalido {
		var tipo = campo(bloco, "TRNTYPE");
		var data = data(campo(bloco, "DTPOSTED"));
		var descricao = descricao(bloco);
		// No OFX do cartão a compra sai negativa (débito); no app, compra é positiva.
		var valor = valor(campo(bloco, "TRNAMT")).negate();
		if (valor.signum() == 0 || "PAYMENT".equalsIgnoreCase(tipo)
				|| (valor.signum() < 0 && Descricoes.normalizar(descricao).startsWith("PAGAMENTO"))) {
			return null;
		}
		Integer atual = null;
		Integer total = null;
		var parcela = parcela(descricao);
		if (parcela != null) {
			atual = parcela[0];
			total = parcela[1];
		}
		var id = idExterno(campo(bloco, "FITID"), data, descricao, valor, atual, total);
		// Alguns bancos repetem o FITID dentro do mesmo arquivo; cada lançamento precisa do seu (RN-11).
		int repeticao = vistos.merge(id, 1, Integer::sum);
		if (repeticao > 1) {
			id = id + "#" + repeticao;
		}
		return new Item(id, data, descricao, valor, atual, total);
	}

	private static String descricao(String bloco) throws ArquivoInvalido {
		var memo = campo(bloco, "MEMO");
		var nome = campo(bloco, "NAME");
		var descricao = memo != null && !memo.isBlank() ? memo : nome;
		if (descricao == null || descricao.isBlank()) {
			throw new ArquivoInvalido("Há um lançamento sem descrição no arquivo.");
		}
		var limpa = descricao.strip().replaceAll("\\s+", " ");
		return limpa.length() > 255 ? limpa.substring(0, 255) : limpa;
	}

	private static String campo(String bloco, String tag) {
		var m = Pattern.compile("(?i)<" + tag + ">([^<\\r\\n]*)").matcher(bloco);
		return m.find() ? desescapar(m.group(1).strip()) : null;
	}

	private static String desescapar(String texto) {
		return texto.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
			.replace("&amp;", "&");
	}

	private static LocalDate data(String texto) throws ArquivoInvalido {
		if (texto == null || texto.length() < 8) {
			throw new ArquivoInvalido("Há um lançamento sem data no arquivo.");
		}
		try {
			return LocalDate.parse(texto.substring(0, 8), DATA);
		}
		catch (DateTimeParseException e) {
			throw new ArquivoInvalido("Não entendi a data " + texto + " no arquivo.");
		}
	}

	/** Aceita "-12.34" (padrão OFX) e "-12,34" ou "-1.234,56" (alguns bancos brasileiros). */
	private static BigDecimal valor(String texto) throws ArquivoInvalido {
		if (texto == null || texto.isBlank()) {
			throw new ArquivoInvalido("Há um lançamento sem valor no arquivo.");
		}
		var limpo = texto.replace(" ", "");
		if (limpo.contains(",")) {
			limpo = limpo.replace(".", "").replace(',', '.');
		}
		try {
			return new BigDecimal(limpo).setScale(2, RoundingMode.HALF_UP);
		}
		catch (NumberFormatException e) {
			throw new ArquivoInvalido("Não entendi o valor " + texto + " no arquivo.");
		}
	}

	/** "PARCELA 3/10", "Parc 03 de 10" ou "LOJA X 03/10" no fim da descrição. */
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

	/** RN-11: FITID; sem ele (ou comprido demais), um hash do lançamento. */
	private static String idExterno(String fitid, LocalDate data, String descricao, BigDecimal valor, Integer atual,
			Integer total) {
		if (fitid != null && !fitid.isBlank() && fitid.length() <= 120) {
			return fitid;
		}
		var base = data + "|" + Descricoes.normalizar(descricao) + "|" + valor.toPlainString() + "|" + atual + "/" + total
				+ "|" + (fitid == null ? "" : fitid);
		try {
			var hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
			return "h:" + HexFormat.of().formatHex(hash, 0, 24);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** O banco informa o saldo devedor; o sinal varia, então vale o valor absoluto. */
	private static BigDecimal saldo(String texto) {
		var m = SALDO.matcher(texto);
		if (!m.find()) {
			return null;
		}
		try {
			var balamt = campo(m.group(1), "BALAMT");
			return balamt == null ? null : valor(balamt).abs();
		}
		catch (ArquivoInvalido e) {
			return null;
		}
	}

	/** OFX 2 é UTF-8; o OFX 1 dos bancos brasileiros costuma vir em windows-1252 (CHARSET:1252). */
	private static String decodificar(byte[] conteudo) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(conteudo))
				.toString()
				.replace("﻿", "")
				.stripLeading();
		}
		catch (CharacterCodingException e) {
			return new String(conteudo, Charset.forName("windows-1252")).stripLeading();
		}
	}

}
