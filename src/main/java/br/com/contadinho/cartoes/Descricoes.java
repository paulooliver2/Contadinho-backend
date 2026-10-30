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

	/**
	 * A compra sem a parcela, igual em todos os meses (RN-17): "LOJA X (Parcela 03 de 10)" e "LOJA X 04/10"
	 * viram "LOJA X".
	 */
	static String daCompra(String descricao) {
		var semParcela = ArquivoFatura.PARCELA_ESCRITA.matcher(descricao).replaceAll(" ");
		semParcela = ArquivoFatura.PARCELA_NO_FIM.matcher(semParcela).replaceAll(" ");
		return normalizar(semParcela);
	}

}
