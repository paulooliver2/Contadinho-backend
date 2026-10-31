package br.com.contadinho.cartoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import br.com.contadinho.cartoes.importacao.ArquivoFatura;
import br.com.contadinho.cartoes.importacao.LeitorOfx;
import org.junit.jupiter.api.Test;

/**
 * Leitor OFX genérico (HU-06, RN-10, RN-11, RN-61), sem banco.
 */
class LeitorOfxTests {

	@Test
	void leOfxSgmlEmWindows1252() throws Exception {
		var arquivo = LeitorOfx.ler("fatura.ofx", ArquivosOfx.sgml());

		assertThat(arquivo.itens()).extracting(ArquivoFatura.Item::descricao)
			.containsExactly("Padaria São João", "Loja de Móveis - Parcela 2/5", "UBER *TRIP", "Estorno Padaria");
		var padaria = arquivo.itens().getFirst();
		assertThat(padaria.idExterno()).isEqualTo("abc-001");
		assertThat(padaria.data()).isEqualTo(LocalDate.of(2026, 10, 3));
		assertThat(padaria.valor()).isEqualByComparingTo("120.50");
		assertThat(arquivo.itens().get(1).parcelaAtual()).isEqualTo(2);
		assertThat(arquivo.itens().get(1).parcelaTotal()).isEqualTo(5);
		assertThat(arquivo.itens().get(2).valor()).isEqualByComparingTo("45.90");
		assertThat(arquivo.itens().get(3).valor()).isEqualByComparingTo("-20.50");
		assertThat(arquivo.ignorados()).isEqualTo(1);
		assertThat(arquivo.totalArquivo()).isEqualByComparingTo(new BigDecimal("445.90"));
		assertThat(arquivo.formato()).isEqualTo("OFX");
		assertThat(arquivo.mes()).isNull();
	}

	@Test
	void leOfxXml() throws Exception {
		var arquivo = LeitorOfx.ler("fatura.ofx", ArquivosOfx.xml());

		assertThat(arquivo.itens()).singleElement().satisfies(item -> {
			assertThat(item.descricao()).isEqualTo("Farmácia & Cia");
			assertThat(item.valor()).isEqualByComparingTo("89.99");
		});
		assertThat(arquivo.totalArquivo()).isNull();
	}

	@Test
	void fitidRepetidoNoArquivoGanhaSufixo() throws Exception {
		var texto = new String(ArquivosOfx.sgml(), "windows-1252").replace("abc-002", "abc-001");

		var arquivo = LeitorOfx.ler("fatura.ofx", texto.getBytes("windows-1252"));

		assertThat(arquivo.itens()).extracting(ArquivoFatura.Item::idExterno).startsWith("abc-001", "abc-001#2");
	}

	@Test
	void semFitidUsaUmHashDoLancamento() throws Exception {
		var texto = new String(ArquivosOfx.xml(), StandardCharsets.UTF_8).replace("<FITID>x-1</FITID>", "");

		var primeira = LeitorOfx.ler("fatura.ofx", texto.getBytes(StandardCharsets.UTF_8));
		var segunda = LeitorOfx.ler("fatura.ofx", texto.getBytes(StandardCharsets.UTF_8));

		assertThat(primeira.itens().getFirst().idExterno()).startsWith("h:")
			.isEqualTo(segunda.itens().getFirst().idExterno());
	}

	@Test
	void recusaCsvEArquivoEstranho() {
		assertThatThrownBy(() -> LeitorOfx.ler("fatura.csv", "data;descricao;valor".getBytes()))
			.hasMessage(LeitorOfx.E_CSV);
		assertThatThrownBy(() -> LeitorOfx.ler("foto.jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2 }))
			.hasMessage(LeitorOfx.NAO_E_OFX);
	}

	@Test
	void arquivoSoComPagamentoNaoTemCompras() {
		var texto = "<OFX><CCSTMTRS><BANKTRANLIST><STMTTRN><TRNTYPE>PAYMENT<DTPOSTED>20261008<TRNAMT>980.00<FITID>p<MEMO>Pagamento</STMTTRN></BANKTRANLIST></CCSTMTRS></OFX>";

		assertThatThrownBy(() -> LeitorOfx.ler("fatura.ofx", texto.getBytes())).hasMessage(ArquivoFatura.SEM_COMPRAS);
	}

	/** O extrato da conta usa o mesmo formato: PIX recebido viraria estorno e PIX enviado sumiria. */
	@Test
	void recusaOExtratoDaContaCorrente() {
		var texto = """
				<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>
				<BANKACCTFROM><BANKID>077<ACCTID>123<ACCTTYPE>CHECKING</BANKACCTFROM>
				<BANKTRANLIST><STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261010<TRNAMT>150.00<FITID>1<MEMO>Pix recebido</STMTTRN></BANKTRANLIST>
				</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
				""";

		assertThatThrownBy(() -> LeitorOfx.ler("Extrato.ofx", texto.getBytes())).hasMessage(LeitorOfx.E_EXTRATO);
	}

	@Test
	void parcelaVemDaDescricao() {
		assertThat(ArquivoFatura.parcela("LOJA X 03/10")).containsExactly(3, 10);
		assertThat(ArquivoFatura.parcela("Notebook Parc 2 de 12")).containsExactly(2, 12);
		assertThat(ArquivoFatura.parcela("AMAZON BR (Parcela 04 de 05)")).containsExactly(4, 5);
		assertThat(ArquivoFatura.parcela("PARCELA 1/1 SEGURO")).containsExactly(1, 1);
		assertThat(ArquivoFatura.parcela("RESTAURANTE 12/05")).isNull();
		assertThat(ArquivoFatura.parcela("UBER 1/1")).isNull();
		assertThat(ArquivoFatura.parcela("Mercado")).isNull();
	}

}
