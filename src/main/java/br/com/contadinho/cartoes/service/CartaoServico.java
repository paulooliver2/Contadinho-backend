package br.com.contadinho.cartoes.service;

import java.util.UUID;

import br.com.contadinho.cartoes.Cartoes;
import br.com.contadinho.cartoes.repository.CartaoRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Cartoes} para os outros módulos. */
@Service
@Transactional(readOnly = true)
public class CartaoServico implements Cartoes {

	private final CartaoRepositorio cartoes;

	public CartaoServico(CartaoRepositorio cartoes) {
		this.cartoes = cartoes;
	}

	@Override
	public boolean ativo(UUID usuarioId, UUID cartaoId) {
		return cartoes.findByIdAndUsuarioIdAndEncerradoEmIsNull(cartaoId, usuarioId).isPresent();
	}

}
