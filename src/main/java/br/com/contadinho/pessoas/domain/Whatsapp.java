package br.com.contadinho.pessoas.domain;

import java.util.Optional;

/**
 * RN-35: o WhatsApp é guardado como +55, DDD e 8 ou 9 dígitos. Aceita o número como a pessoa digita ou como vem
 * dos contatos: "(61) 99999-8888", "61 9999-8888", "+55 61 99999-8888", "0 61 99999 8888".
 */
public final class Whatsapp {

	private Whatsapp() {
	}

	/** Vazio quando o texto não é um número brasileiro com DDD. */
	public static Optional<String> normalizar(String texto) {
		var digitos = texto.replaceAll("\\D", "");
		// Prefixo de longa distância (0 ou 0 + operadora) que vem em alguns contatos antigos.
		if (digitos.startsWith("0") && (digitos.length() == 11 || digitos.length() == 12)) {
			digitos = digitos.substring(1);
		}
		if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
			digitos = digitos.substring(2);
		}
		// DDD de 11 a 99, sem zero; celular tem 9 dígitos começando com 9, fixo tem 8.
		var valido = digitos.matches("[1-9][1-9](9\\d{8}|[2-8]\\d{7})");
		return valido ? Optional.of("+55" + digitos) : Optional.empty();
	}

}
