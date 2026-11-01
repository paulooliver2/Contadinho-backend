package br.com.contadinho.gastos.service;

import java.util.UUID;

import br.com.contadinho.cartoes.UsoDoCartao;
import br.com.contadinho.gastos.repository.ContaFixaRepositorio;
import br.com.contadinho.gastos.repository.GastoRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Conta fixa ou gasto pago com o cartão impede excluí-lo (HU-45). */
@Service
@Transactional(readOnly = true)
public class UsoDoCartaoEmGastos implements UsoDoCartao {

	private final ContaFixaRepositorio contas;

	private final GastoRepositorio gastos;

	public UsoDoCartaoEmGastos(ContaFixaRepositorio contas, GastoRepositorio gastos) {
		this.contas = contas;
		this.gastos = gastos;
	}

	@Override
	public boolean usa(UUID usuarioId, UUID cartaoId) {
		return contas.existsByUsuarioIdAndCartaoId(usuarioId, cartaoId) || gastos.existsByUsuarioIdAndCartaoId(usuarioId, cartaoId);
	}

}
