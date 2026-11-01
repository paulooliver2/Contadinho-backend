package br.com.contadinho.gastos.service;

import br.com.contadinho.cartoes.ComprasImportadas;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * HU-14.1 e RN-26: a fatura importada traz compras novas; as contas fixas e os gastos pagos naquele cartão procuram o
 * lançamento deles. Escuta dentro da transação da importação, então tudo muda junto ou não muda.
 */
@Component
public class ComprasImportadasOuvinte {

	private final ContaFixaServico contas;

	private final GastoServico gastos;

	public ComprasImportadasOuvinte(ContaFixaServico contas, GastoServico gastos) {
		this.contas = contas;
		this.gastos = gastos;
	}

	/** As contas fixas escolhem primeiro; o gasto fica com o que sobrar. */
	@EventListener
	public void importadas(ComprasImportadas evento) {
		contas.ligarImportadas(evento.usuarioId(), evento.cartaoId(), evento.lancamentoIds());
		gastos.ligarImportadas(evento.usuarioId(), evento.cartaoId(), evento.lancamentoIds());
	}

}
