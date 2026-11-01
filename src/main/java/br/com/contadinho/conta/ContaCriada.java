package br.com.contadinho.conta;

import java.util.UUID;

/**
 * Evento publicado quando alguém cria a conta (HU-01), dentro da mesma transação. Os outros módulos preparam o que
 * o dono novo precisa, como as categorias padrão (HU-15.1).
 */
public record ContaCriada(UUID usuarioId) {
}
