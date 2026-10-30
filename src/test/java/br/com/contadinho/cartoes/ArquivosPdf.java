package br.com.contadinho.cartoes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Faturas em PDF geradas no teste, com dados fictícios no layout do banco. */
final class ArquivosPdf {

	private ArquivosPdf() {
	}

	/**
	 * Fatura do Inter com dois cartões: um pagamento (ignorado), uma compra, um Pix parcelado, uma parcela de
	 * compra antiga e um estorno. Soma: 120,50 + 312,62 + 119,98 - 85,70 = 467,40. A linha depois de "Próxima
	 * fatura" não pode entrar.
	 */
	static byte[] inter(String senha) {
		return pdf(senha, List.of(
				"Resumo da fatura",
				"FULANO DE TAL",
				"5364****1111 12/11/2026 R$ 467,40",
				"Despesas da fatura",
				"CARTÃO 5364****1111",
				"Data Movimentação Beneficiário Valor",
				"12 de out. 2026 PAGAMENTO ON LINE - + R$ 980,00",
				"03 de out. 2026 PADARIA SÃO JOÃO - R$ 120,50",
				"18 de set. 2026 PIX CRED PARCELADO (Parcela 01 de 02) FULANO R$ 312,62",
				"Principal (R$ 263,17) + Juros (R$ 49,45)",
				"Total CARTÃO 5364****1111 R$ 433,12",
				"CARTÃO 5364****2222",
				"Data Movimentação Beneficiário Valor",
				"21 de mar. 2026 MERCADOLIVRE (Parcela 07 de 10) - R$ 119,98",
				"16 de out. 2026 BUS SERVICOS*CLICKBUS - + R$ 85,70",
				"Total CARTÃO 5364****2222 R$ 34,28",
				"Próxima fatura",
				"02 de nov. 2026 NAO DEVE ENTRAR - R$ 10,00",
				"BANCO INTER S/A CNPJ: 00.416.968/0001-01"));
	}

	/** Um PDF com as linhas dadas, uma por linha, protegido por senha quando ela não é null. */
	static byte[] pdf(String senha, List<String> linhas) {
		try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream()) {
			var pagina = new PDPage();
			documento.addPage(pagina);
			if (!linhas.isEmpty()) {
				try (var conteudo = new PDPageContentStream(documento, pagina)) {
					conteudo.beginText();
					conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
					conteudo.setLeading(12);
					conteudo.newLineAtOffset(30, 760);
					for (var linha : linhas) {
						conteudo.showText(linha);
						conteudo.newLine();
					}
					conteudo.endText();
				}
			}
			if (senha != null) {
				var protecao = new StandardProtectionPolicy("dono-" + senha, senha, new AccessPermission());
				protecao.setEncryptionKeyLength(128);
				documento.protect(protecao);
			}
			documento.save(saida);
			return saida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
