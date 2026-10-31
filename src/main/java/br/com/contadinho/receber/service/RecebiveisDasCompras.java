package br.com.contadinho.receber.service;

import br.com.contadinho.cartoes.ResponsavelDaCompra;
import br.com.contadinho.receber.domain.Recebivel;
import br.com.contadinho.receber.repository.RecebivelRepositorio;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * RN-16: a compra atribuída a uma pessoa tem um recebível, ligado à fatura. Escuta dentro da transação de quem
 * atribuiu, então a compra e o recebível mudam juntos ou não mudam.
 */
@Component
public class RecebiveisDasCompras {

	private final RecebivelRepositorio recebiveis;

	public RecebiveisDasCompras(RecebivelRepositorio recebiveis) {
		this.recebiveis = recebiveis;
	}

	@EventListener
	public void responsavelMudou(ResponsavelDaCompra evento) {
		var atual = recebiveis.findByLancamentoId(evento.lancamentoId());
		if (evento.pessoaId() == null) {
			// Voltou para o dono: não há o que receber.
			atual.ifPresent(recebiveis::delete);
			return;
		}
		var recebivel = atual.orElseGet(() -> Recebivel.daCompra(evento.usuarioId(), evento.lancamentoId()));
		recebivel.acompanhar(evento.pessoaId(), evento.valor(), evento.vencimento());
		recebiveis.save(recebivel);
	}

}
