package br.com.contadinho.gastos.service;

import br.com.contadinho.cartoes.ComprasImportadas;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * HU-14.1: a fatura importada traz compras novas; as contas fixas pagas naquele cartão procuram o lançamento delas.
 * Escuta dentro da transação da importação, então as duas coisas mudam juntas ou não mudam.
 */
@Component
public class ComprasImportadasOuvinte {

	private final ContaFixaServico servico;

	public ComprasImportadasOuvinte(ContaFixaServico servico) {
		this.servico = servico;
	}

	@EventListener
	public void importadas(ComprasImportadas evento) {
		servico.ligarImportadas(evento.usuarioId(), evento.cartaoId(), evento.lancamentoIds());
	}

}
