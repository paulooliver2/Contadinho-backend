package br.com.contadinho.receber;

import br.com.contadinho.cartoes.ResponsavelDaCompra;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * RN-16: a compra atribuída a uma pessoa tem um recebível, ligado à fatura. Escuta dentro da transação de quem
 * atribuiu, então a compra e o recebível mudam juntos ou não mudam.
 */
@Component
class RecebiveisDasCompras {

	private final RecebivelRepositorio recebiveis;

	RecebiveisDasCompras(RecebivelRepositorio recebiveis) {
		this.recebiveis = recebiveis;
	}

	@EventListener
	void responsavelMudou(ResponsavelDaCompra evento) {
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
