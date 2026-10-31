package br.com.contadinho.cartoes.importacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fatura do cartão Inter em PDF. Cada compra é uma linha "05 de set. 2026 DESCRIÇÃO BENEFICIÁRIO R$ 34,45",
 * agrupada sob "CARTÃO 5364****3963"; crédito (pagamento, estorno) vem com "+ R$". O beneficiário vazio sai
 * como "-". O cabeçalho de cada página repete "5364****9453 12/10/2026 R$ 7.639,06": vencimento e total.
 */
public final class ModeloPdfInter implements LeitorPdf.Modelo {

	private static final List<String> MESES = List.of("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set",
			"out", "nov", "dez");

	private static final Pattern LANCAMENTO = Pattern.compile(
			"^(\\d{1,2}) de (jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)\\.? (\\d{4}) (.+?) (\\+ )?R\\$ (\\d{1,3}(?:\\.\\d{3})*,\\d{2})$");

	private static final Pattern CARTAO = Pattern.compile("^CART[ÃA]O \\d{4}\\*{4}(\\d{4})$");

	private static final Pattern CABECALHO = Pattern.compile("\\d{4}\\*{4}\\d{4} (\\d{2})/(\\d{2})/(\\d{4}) R\\$ (\\d{1,3}(?:\\.\\d{3})*,\\d{2})");

	@Override
	public boolean reconhece(String texto) {
		var maiusculo = texto.toUpperCase(Locale.ROOT);
		return maiusculo.contains("BANCO INTER") || maiusculo.contains("BANCOINTER.COM.BR");
	}

	@Override
	public ArquivoFatura ler(String texto) throws ArquivoInvalido {
		var montador = new ArquivoFatura.Montador();
		String finalCartao = null;
		for (var linha : texto.lines().map(String::strip).toList()) {
			// Daqui em diante são as parcelas que vão para a próxima fatura.
			if (linha.equalsIgnoreCase("Próxima fatura")) {
				break;
			}
			var cartao = CARTAO.matcher(linha);
			if (cartao.matches()) {
				finalCartao = cartao.group(1);
				continue;
			}
			var m = LANCAMENTO.matcher(linha);
			if (!m.matches()) {
				continue;
			}
			var data = LocalDate.of(Integer.parseInt(m.group(3)), MESES.indexOf(m.group(2)) + 1, Integer.parseInt(m.group(1)));
			var descricao = m.group(4).replaceFirst("\\s+-$", "");
			var valor = LeitorOfx.valor(m.group(6));
			montador.adicionar(null, data, descricao, m.group(5) == null ? valor : valor.negate(), false, finalCartao);
		}

		YearMonth mes = null;
		BigDecimal total = null;
		var cabecalho = CABECALHO.matcher(texto);
		if (cabecalho.find()) {
			mes = YearMonth.of(Integer.parseInt(cabecalho.group(3)), Integer.parseInt(cabecalho.group(2)));
			total = LeitorOfx.valor(cabecalho.group(4));
		}
		return montador.montar("PDF", "PDF_INTER", mes, total);
	}

}
