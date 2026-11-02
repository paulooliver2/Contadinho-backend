package br.com.contadinho.receber.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.PagoDasCompras;
import br.com.contadinho.receber.domain.Pagamento;
import br.com.contadinho.receber.domain.Recebivel;
import br.com.contadinho.receber.repository.PagamentoRepositorio;
import br.com.contadinho.receber.repository.RecebivelRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link PagoDasCompras}: o que os pagamentos já cobriram de cada compra, abatendo do que vence primeiro (RN-30). */
@Service
@Transactional(readOnly = true)
public class PagoDasComprasServico implements PagoDasCompras {

	private final RecebivelRepositorio recebiveis;

	private final PagamentoRepositorio pagamentos;

	public PagoDasComprasServico(RecebivelRepositorio recebiveis, PagamentoRepositorio pagamentos) {
		this.recebiveis = recebiveis;
		this.pagamentos = pagamentos;
	}

	@Override
	public Map<UUID, BigDecimal> pago(UUID usuarioId, Collection<UUID> lancamentoIds) {
		var compras = new HashSet<>(lancamentoIds);
		var pagoPorPessoa = pagamentos.findByUsuarioIdAndDesfeitoEmIsNull(usuarioId).stream()
			.collect(Collectors.groupingBy(Pagamento::getPessoaId,
					Collectors.reducing(BigDecimal.ZERO, Pagamento::getValor, BigDecimal::add)));
		var resultado = new TreeMap<UUID, BigDecimal>();
		recebiveis.findByUsuarioId(usuarioId).stream()
			.collect(Collectors.groupingBy(Recebivel::getPessoaId))
			.forEach((pessoa, daPessoa) -> {
				var pago = pagoPorPessoa.getOrDefault(pessoa, BigDecimal.ZERO);
				if (pago.signum() == 0) {
					return;
				}
				var aberto = ReceberServico.emAberto(daPessoa, pago);
				var coberto = daPessoa.stream()
					.filter(r -> r.getLancamentoId() != null && compras.contains(r.getLancamentoId())
							&& r.getValor().signum() > 0)
					.map(r -> r.getValor().subtract(aberto.getOrDefault(r.getId(), r.getValor())))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
				if (coberto.signum() > 0) {
					resultado.put(pessoa, coberto);
				}
			});
		return resultado;
	}

}
