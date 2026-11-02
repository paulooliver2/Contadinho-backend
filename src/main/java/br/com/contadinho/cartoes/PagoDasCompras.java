package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Implementado pelo A receber: quanto de cada compra as pessoas já pagaram (RN-30). Desfazer uma importação avisa
 * antes que esses pagamentos vão abater o resto ou virar crédito (HU-44.2, RN-16).
 */
public interface PagoDasCompras {

	/** Por pessoa, a parte destas compras já coberta pelos pagamentos; quem não pagou nada delas não aparece. */
	Map<UUID, BigDecimal> pago(UUID usuarioId, Collection<UUID> lancamentoIds);

}
