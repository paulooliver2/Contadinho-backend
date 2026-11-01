package br.com.contadinho.conta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.contadinho.conta.domain.ChavePix;
import br.com.contadinho.conta.domain.ChavePix.Tipo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * HU-03.1: cada tipo de chave pelo seu formato.
 */
class ChavePixTests {

	@ParameterizedTest
	@CsvSource({
			"CPF, 529.982.247-25, 52998224725",
			"CPF, 52998224725, 52998224725",
			"EMAIL, ' Voce@Email.com ', voce@email.com",
			"CELULAR, (61) 99999-8888, +5561999998888",
			"CELULAR, +55 61 99999-8888, +5561999998888",
			"ALEATORIA, 3F2504E0-4F89-11D3-9A0C-0305E82C3301, 3f2504e0-4f89-11d3-9a0c-0305e82c3301" })
	void normaliza(Tipo tipo, String digitado, String guardado) {
		assertThat(ChavePix.normalizar(tipo, digitado)).contains(guardado);
	}

	@ParameterizedTest
	@CsvSource({
			"CPF, 529.982.247-24",
			"CPF, 111.111.111-11",
			"CPF, 5299822472",
			"EMAIL, voce@email",
			"EMAIL, voce email@x.com",
			"CELULAR, (61) 3333-4444",
			"CELULAR, 99999-8888",
			"ALEATORIA, 3f2504e04f8911d39a0c0305e82c3301" })
	void recusaForaDoFormato(Tipo tipo, String digitado) {
		assertThat(ChavePix.normalizar(tipo, digitado)).isEmpty();
	}

	@Test
	void emailAcimaDoLimiteDoPix() {
		var longo = "a".repeat(70) + "@email.com";

		assertThat(ChavePix.normalizar(Tipo.EMAIL, longo)).isEmpty();
	}

}
