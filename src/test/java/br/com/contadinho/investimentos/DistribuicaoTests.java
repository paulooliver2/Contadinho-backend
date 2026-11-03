package br.com.contadinho.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import br.com.contadinho.investimentos.domain.Distribuicao;
import br.com.contadinho.investimentos.domain.Distribuicao.Classe;
import br.com.contadinho.investimentos.domain.Investimento;
import org.junit.jupiter.api.Test;

/** HU-32: a carteira dividida em renda fixa, ações (com ETFs), FIIs e cripto. */
class DistribuicaoTests {

	@Test
	void somaCem() {
		var fatias = Distribuicao.de(Map.of(Classe.RENDA_FIXA, new BigDecimal("5000"), Classe.ACOES,
				new BigDecimal("3000"), Classe.FIIS, new BigDecimal("1500"), Classe.CRIPTO, new BigDecimal("500")));

		assertThat(fatias).extracting(Distribuicao.Fatia::classe)
			.containsExactly(Classe.RENDA_FIXA, Classe.ACOES, Classe.FIIS, Classe.CRIPTO);
		assertThat(fatias).extracting(f -> f.percentual().toPlainString())
			.containsExactly("50.00", "30.00", "15.00", "5.00");

		var tercos = Distribuicao.de(Map.of(Classe.ACOES, BigDecimal.ONE, Classe.FIIS, BigDecimal.ONE, Classe.CRIPTO,
				BigDecimal.ONE, Classe.RENDA_FIXA, BigDecimal.ZERO));

		assertThat(tercos).extracting(Distribuicao.Fatia::classe)
			.containsExactly(Classe.ACOES, Classe.FIIS, Classe.CRIPTO);
		assertThat(tercos).extracting(f -> f.percentual().toPlainString()).containsExactly("33.34", "33.33", "33.33");
	}

	@Test
	void classes() {
		var agora = Instant.now();
		var usuario = UUID.randomUUID();
		var tesouro = new Investimento(usuario, Investimento.Tipo.TESOURO, "Tesouro Selic 2029", "Tesouro Selic 2029",
				null, true, agora);
		var fii = new Investimento(usuario, Investimento.Tipo.ACAO, "HGLG11", "CSHG Logística", "HGLG11", true, agora);
		fii.setFii(true);
		var etf = new Investimento(usuario, Investimento.Tipo.ACAO, "BOVA11", "iShares Ibovespa", "BOVA11", true, agora);

		assertThat(Distribuicao.classe(tesouro)).isEqualTo(Classe.RENDA_FIXA);
		assertThat(Distribuicao.classe(fii)).isEqualTo(Classe.FIIS);
		assertThat(Distribuicao.classe(etf)).isEqualTo(Classe.ACOES);
	}

}
