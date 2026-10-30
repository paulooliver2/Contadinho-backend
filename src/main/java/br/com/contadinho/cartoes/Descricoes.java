package br.com.contadinho.cartoes;

import java.text.Normalizer;
import java.util.Locale;

/** Descrição normalizada, usada para lembrar responsáveis (RN-18) e casar estornos (RN-20). */
final class Descricoes {

	private Descricoes() {
	}

	/** "Padaria  São João*" vira "PADARIA SAO JOAO". */
	static String normalizar(String descricao) {
		var semAcentos = Normalizer.normalize(descricao, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcentos.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").strip();
	}

}
