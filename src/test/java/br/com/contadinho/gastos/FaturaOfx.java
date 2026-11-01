package br.com.contadinho.gastos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import br.com.contadinho.ClienteTeste;

/** Importa uma fatura OFX montada no teste, para ligar contas fixas e gastos ao lançamento (HU-14, RN-26). */
final class FaturaOfx {

	private FaturaOfx() {
	}

	/** Uma compra do arquivo. */
	record Compra(String idExterno, LocalDate data, String valor, String descricao) {
	}

	static void importar(ClienteTeste api, String dono, String cartaoId, YearMonth fatura, Compra... compras) {
		var ofx = new StringBuilder("""
				OFXHEADER:100
				DATA:OFXSGML
				VERSION:102
				ENCODING:USASCII
				CHARSET:1252

				<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL<BANKTRANLIST>
				""");
		for (var c : compras) {
			ofx.append("<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>").append(c.data().format(DateTimeFormatter.BASIC_ISO_DATE))
				.append("<TRNAMT>-").append(c.valor()).append("<FITID>").append(c.idExterno()).append("<MEMO>")
				.append(c.descricao()).append("</STMTTRN>\n");
		}
		ofx.append("</BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>\n");
		var corpo = Map.<String, Object>of("nomeArquivo", "fatura.ofx", "conteudo",
				Base64.getEncoder().encodeToString(ofx.toString().getBytes(StandardCharsets.US_ASCII)), "mes",
				fatura.toString(), "mesmas", List.of());
		assertThat(api.post("/api/cartoes/" + cartaoId + "/importacoes", corpo, dono).status()).isEqualTo(201);
	}

}
