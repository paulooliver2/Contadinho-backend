package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Leitor de PDF e modelo do Inter (HU-39, RN-11, RN-19, RN-61), sem banco.
 */
class LeitorPdfTests {

	@Test
	void leAFaturaDoInter() throws Exception {
		var arquivo = LeitorFatura.ler("Fatura.pdf", ArquivosPdf.inter(null), null);

		assertThat(arquivo.formato()).isEqualTo("PDF");
		assertThat(arquivo.modelo()).isEqualTo("PDF_INTER");
		assertThat(arquivo.mes()).isEqualTo(YearMonth.of(2026, 11));
		assertThat(arquivo.totalArquivo()).isEqualByComparingTo("467.40");
		assertThat(arquivo.ignorados()).isEqualTo(1);
		assertThat(arquivo.itens()).extracting(ArquivoFatura.Item::descricao).containsExactly(
				"PADARIA SÃO JOÃO",
				"PIX CRED PARCELADO (Parcela 01 de 02) FULANO",
				"MERCADOLIVRE (Parcela 07 de 10)",
				"BUS SERVICOS*CLICKBUS");
		assertThat(arquivo.itens()).extracting(ArquivoFatura.Item::finalCartao).containsExactly("1111", "1111", "2222", "2222");

		var mercadoLivre = arquivo.itens().get(2);
		assertThat(mercadoLivre.data()).isEqualTo(LocalDate.of(2026, 3, 21));
		assertThat(mercadoLivre.parcelaAtual()).isEqualTo(7);
		assertThat(mercadoLivre.parcelaTotal()).isEqualTo(10);
		assertThat(arquivo.itens().getLast().valor()).isEqualByComparingTo("-85.70");
		assertThat(arquivo.itens().stream().map(ArquivoFatura.Item::valor).reduce(BigDecimal.ZERO, BigDecimal::add))
			.isEqualByComparingTo(arquivo.totalArquivo());
	}

	@Test
	void idsSaoOsMesmosAoLerDeNovo() throws Exception {
		var primeira = LeitorFatura.ler("Fatura.pdf", ArquivosPdf.inter(null), null);
		var segunda = LeitorFatura.ler("Fatura.pdf", ArquivosPdf.inter(null), null);

		assertThat(primeira.itens()).extracting(ArquivoFatura.Item::idExterno)
			.doesNotHaveDuplicates()
			.containsExactlyElementsOf(segunda.itens().stream().map(ArquivoFatura.Item::idExterno).toList());
	}

	@Test
	void duasComprasIguaisNoMesmoDiaSaoDuas() throws Exception {
		var linha = "13 de set. 2026 JET PATINETES - R$ 10,00";
		var pdf = ArquivosPdf.pdf(null, List.of("5364****1111 12/10/2026 R$ 20,00", "CARTÃO 5364****1111", linha, linha, "BANCO INTER S/A"));

		var arquivo = LeitorFatura.ler("Fatura.pdf", pdf, null);

		assertThat(arquivo.itens()).hasSize(2).extracting(ArquivoFatura.Item::idExterno).doesNotHaveDuplicates();
	}

	@Test
	void pdfComSenhaPedeASenha() throws Exception {
		var pdf = ArquivosPdf.inter("12345");

		assertThatThrownBy(() -> LeitorFatura.ler("Fatura.pdf", pdf, null))
			.isInstanceOfSatisfying(ArquivoInvalido.class, e -> {
				assertThat(e.codigo()).isEqualTo(ArquivoInvalido.Codigo.SENHA_PDF);
				assertThat(e).hasMessage(LeitorPdf.PEDE_SENHA);
			});
		assertThatThrownBy(() -> LeitorFatura.ler("Fatura.pdf", pdf, "99999"))
			.isInstanceOfSatisfying(ArquivoInvalido.class,
					e -> assertThat(e.codigo()).isEqualTo(ArquivoInvalido.Codigo.SENHA_PDF_INCORRETA));
		assertThat(LeitorFatura.ler("Fatura.pdf", pdf, "12345").itens()).hasSize(4);
	}

	@Test
	void pdfDeBancoSemModeloOuSemTextoERecusado() {
		var outroBanco = ArquivosPdf.pdf(null, List.of("Fatura do cartão", "05/10 MERCADO 34,45"));
		var semTexto = ArquivosPdf.pdf(null, List.of());

		assertThatThrownBy(() -> LeitorFatura.ler("Fatura.pdf", outroBanco, null)).hasMessage(LeitorPdf.BANCO_SEM_MODELO);
		assertThatThrownBy(() -> LeitorFatura.ler("Fatura.pdf", semTexto, null)).hasMessage(LeitorPdf.SEM_TEXTO);
	}

}
