package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor OFX genérico (HU-06, HU-37): aceita o OFX 1 (SGML, tags sem fechamento) e o OFX 2 (XML).
 * Não depende do banco; os modelos por banco entram depois, por cima dele.
 */
final class LeitorOfx {

	static final String NAO_E_OFX = "Não reconheci este arquivo como fatura. Importe o PDF ou o OFX da fatura do cartão.";

	static final String E_CSV = "A importação de CSV ainda não está disponível. Importe o PDF ou o OFX da fatura do cartão.";

	static final String E_EXTRATO = "Este OFX é o extrato da conta corrente, não a fatura do cartão. "
			+ "Importe o PDF ou o OFX da fatura do cartão de crédito.";

	private static final Pattern TRANSACAO = Pattern.compile("(?is)<STMTTRN>(.*?)(?=</STMTTRN>|<STMTTRN>|</BANKTRANLIST>|$)");

	private static final Pattern SALDO = Pattern.compile("(?is)<LEDGERBAL>(.*?)(?=</LEDGERBAL>|<AVAILBAL>|$)");

	private static final DateTimeFormatter DATA = DateTimeFormatter.BASIC_ISO_DATE;

	private LeitorOfx() {
	}

	static ArquivoFatura ler(String nomeArquivo, byte[] conteudo) throws ArquivoInvalido {
		var texto = decodificar(conteudo);
		var maiusculo = texto.toUpperCase();
		if (!maiusculo.contains("<OFX>")) {
			throw new ArquivoInvalido(nomeArquivo.toLowerCase().endsWith(".csv") ? E_CSV : NAO_E_OFX);
		}
		// O mesmo formato serve para o extrato da conta (STMTRS); só o bloco do cartão (CCSTMTRS) é fatura.
		if (!maiusculo.contains("<CCSTMTRS>")) {
			throw new ArquivoInvalido(maiusculo.contains("<STMTRS>") ? E_EXTRATO : NAO_E_OFX);
		}

		var montador = new ArquivoFatura.Montador();
		Matcher transacoes = TRANSACAO.matcher(texto);
		while (transacoes.find()) {
			var bloco = transacoes.group(1);
			// No OFX do cartão a compra sai negativa (débito); no app, compra é positiva.
			montador.adicionar(campo(bloco, "FITID"), data(campo(bloco, "DTPOSTED")), descricao(bloco),
					valor(campo(bloco, "TRNAMT")).negate(), "PAYMENT".equalsIgnoreCase(campo(bloco, "TRNTYPE")), null);
		}
		return montador.montar("OFX", "OFX_GENERICO", null, saldo(texto));
	}

	private static String descricao(String bloco) throws ArquivoInvalido {
		var memo = campo(bloco, "MEMO");
		var nome = campo(bloco, "NAME");
		var descricao = memo != null && !memo.isBlank() ? memo : nome;
		if (descricao == null || descricao.isBlank()) {
			throw new ArquivoInvalido("Há um lançamento sem descrição no arquivo.");
		}
		return descricao;
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
	static BigDecimal valor(String texto) throws ArquivoInvalido {
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
