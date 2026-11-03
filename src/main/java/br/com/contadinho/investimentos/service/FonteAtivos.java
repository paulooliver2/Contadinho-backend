package br.com.contadinho.investimentos.service;

import java.util.List;

/**
 * Onde o app confere se o ativo existe (HU-30.1): o ticker na B3 e a cripto pela busca. O preço fica para a HU-31.
 * Erro de rede, cota ou falha da fonte é {@link Conferencia.Indisponivel}, nunca exceção.
 */
public interface FonteAtivos {

	Conferencia conferirTicker(String ticker);

	BuscaCripto buscarCripto(String texto);

	Conferencia conferirCripto(String id);

	sealed interface Conferencia {

		record Existe(String nome, String simbolo) implements Conferencia {
		}

		record NaoExiste() implements Conferencia {
		}

		record Indisponivel() implements Conferencia {
		}

	}

	record Cripto(String id, String simbolo, String nome) {
	}

	sealed interface BuscaCripto {

		record Encontradas(List<Cripto> moedas) implements BuscaCripto {
		}

		record Indisponivel() implements BuscaCripto {
		}

	}

}
