package br.com.contadinho.conta.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * HU-03.1: a chave Pix validada pelo formato do tipo e guardada num jeito só. CPF fica só com os dígitos, e-mail em
 * minúsculas, celular como +55 com DDD e a aleatória em minúsculas com os hífens.
 */
public final class ChavePix {

	public enum Tipo {
		CPF, EMAIL, CELULAR, ALEATORIA
	}

	/** O limite da coluna; uma chave de e-mail do Pix tem até 77 caracteres. */
	public static final int TAMANHO_MAXIMO = 77;

	private ChavePix() {
	}

	/** Vazio quando o texto não é uma chave daquele tipo. */
	public static Optional<String> normalizar(Tipo tipo, String texto) {
		var limpo = texto.strip();
		return switch (tipo) {
			case CPF -> cpf(limpo.replaceAll("[\\s.\\-]", ""));
			case EMAIL -> Optional.of(limpo.toLowerCase(Locale.ROOT))
				.filter(e -> e.length() <= TAMANHO_MAXIMO && e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"));
			case CELULAR -> celular(limpo.replaceAll("\\D", ""));
			case ALEATORIA -> Optional.of(limpo.toLowerCase(Locale.ROOT))
				.filter(a -> a.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
		};
	}

	/** Os dois dígitos verificadores conferem e não são onze dígitos iguais. */
	private static Optional<String> cpf(String digitos) {
		if (!digitos.matches("\\d{11}") || digitos.chars().distinct().count() == 1) {
			return Optional.empty();
		}
		for (int posicao = 9; posicao <= 10; posicao++) {
			int soma = 0;
			for (int i = 0; i < posicao; i++) {
				soma += (digitos.charAt(i) - '0') * (posicao + 1 - i);
			}
			int verificador = soma * 10 % 11 % 10;
			if (verificador != digitos.charAt(posicao) - '0') {
				return Optional.empty();
			}
		}
		return Optional.of(digitos);
	}

	/** Chave de celular: DDD e 9 dígitos começando com 9, com ou sem o +55. */
	private static Optional<String> celular(String digitos) {
		if (digitos.startsWith("55") && digitos.length() == 13) {
			digitos = digitos.substring(2);
		}
		return digitos.matches("[1-9][1-9]9\\d{8}") ? Optional.of("+55" + digitos) : Optional.empty();
	}

}
