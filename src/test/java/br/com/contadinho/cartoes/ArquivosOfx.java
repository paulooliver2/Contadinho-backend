package br.com.contadinho.cartoes;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Faturas OFX de exemplo, nos dois formatos que os bancos exportam. */
final class ArquivosOfx {

	private ArquivosOfx() {
	}

	/**
	 * OFX 1 (SGML) em windows-1252, como o Nubank e o Itaú exportam: compras negativas, um pagamento da fatura
	 * anterior, uma parcela e um estorno. Total da fatura: 120,50 + 300,00 + 45,90 - 20,50 = 445,90.
	 */
	static byte[] sgml() {
		return """
				OFXHEADER:100
				DATA:OFXSGML
				VERSION:102
				ENCODING:USASCII
				CHARSET:1252

				<OFX>
				<CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS>
				<CURDEF>BRL
				<BANKTRANLIST>
				<DTSTART>20260929
				<DTEND>20261028
				<STMTTRN>
				<TRNTYPE>DEBIT
				<DTPOSTED>20261003000000[-3:BRT]
				<TRNAMT>-120.50
				<FITID>abc-001
				<MEMO>Padaria São João
				</STMTTRN>
				<STMTTRN>
				<TRNTYPE>DEBIT
				<DTPOSTED>20261005
				<TRNAMT>-300.00
				<FITID>abc-002
				<MEMO>Loja de Móveis - Parcela 2/5
				</STMTTRN>
				<STMTTRN>
				<TRNTYPE>CREDIT
				<DTPOSTED>20261008
				<TRNAMT>980.00
				<FITID>abc-003
				<MEMO>Pagamento recebido
				</STMTTRN>
				<STMTTRN>
				<TRNTYPE>DEBIT
				<DTPOSTED>20261020
				<TRNAMT>-45,90
				<FITID>abc-004
				<NAME>UBER *TRIP
				</STMTTRN>
				<STMTTRN>
				<TRNTYPE>CREDIT
				<DTPOSTED>20261021
				<TRNAMT>20.50
				<FITID>abc-005
				<MEMO>Estorno Padaria
				</STMTTRN>
				</BANKTRANLIST>
				<LEDGERBAL><BALAMT>-445.90<DTASOF>20261028</LEDGERBAL>
				</CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1>
				</OFX>
				""".getBytes(Charset.forName("windows-1252"));
	}

	/** OFX 2 (XML) em UTF-8, sem saldo, com uma compra só. */
	static byte[] xml() {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<?OFX OFXHEADER="200" VERSION="211"?>
				<OFX>
				  <CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><BANKTRANLIST>
				    <STMTTRN>
				      <TRNTYPE>DEBIT</TRNTYPE>
				      <DTPOSTED>20261012</DTPOSTED>
				      <TRNAMT>-89.99</TRNAMT>
				      <FITID>x-1</FITID>
				      <NAME>Farmácia &amp; Cia</NAME>
				    </STMTTRN>
				  </BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1>
				</OFX>
				""".getBytes(StandardCharsets.UTF_8);
	}

}
