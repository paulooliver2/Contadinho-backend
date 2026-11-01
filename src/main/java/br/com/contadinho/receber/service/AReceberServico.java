package br.com.contadinho.receber.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.contadinho.cartoes.Compras;
import br.com.contadinho.receber.AReceber;
import br.com.contadinho.receber.domain.Pagamento;
import br.com.contadinho.receber.domain.Recebivel;
import br.com.contadinho.receber.repository.PagamentoRepositorio;
import br.com.contadinho.receber.repository.RecebivelRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link AReceber} para os outros módulos. */
@Service
public class AReceberServico implements AReceber {

	private final ReceberServico receber;

	private final RecebivelRepositorio recebiveis;

	private final PagamentoRepositorio pagamentos;

	private final Compras compras;

	public AReceberServico(ReceberServico receber, RecebivelRepositorio recebiveis, PagamentoRepositorio pagamentos,
			Compras compras) {
		this.receber = receber;
		this.recebiveis = recebiveis;
		this.pagamentos = pagamentos;
		this.compras = compras;
	}

	@Override
	@Transactional(readOnly = true)
	public SituacaoAReceber situacao(UUID usuarioId) {
		var devem = receber.resumo(usuarioId).pessoas().stream().filter(s -> s.saldo().signum() > 0).toList();
		var original = devem.stream().map(ReceberServico.Situacao::original).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new SituacaoAReceber(devem.stream().map(ReceberServico.Situacao::saldo).reduce(BigDecimal.ZERO,
				BigDecimal::add), original,
				devem.stream()
					.map(s -> new Devedor(s.pessoaId(), s.saldo(), s.status() == ReceberServico.Status.PAGOU_PARTE))
					.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public List<CompraEmAberto> comprasEmAberto(UUID usuarioId) {
		var pagoPorPessoa = pagamentos.findByUsuarioIdAndDesfeitoEmIsNull(usuarioId).stream()
			.collect(Collectors.groupingBy(Pagamento::getPessoaId,
					Collectors.reducing(BigDecimal.ZERO, Pagamento::getValor, BigDecimal::add)));
		var abertoPorLancamento = new HashMap<UUID, Recebivel>();
		var aberto = new HashMap<UUID, BigDecimal>();
		recebiveis.findByUsuarioId(usuarioId).stream()
			.collect(Collectors.groupingBy(Recebivel::getPessoaId))
			.forEach((pessoa, daPessoa) -> {
				aberto.putAll(ReceberServico.emAberto(daPessoa, pagoPorPessoa.getOrDefault(pessoa, BigDecimal.ZERO)));
				daPessoa.stream()
					.filter(r -> r.getLancamentoId() != null && aberto.get(r.getId()).signum() > 0)
					.forEach(r -> abertoPorLancamento.put(r.getLancamentoId(), r));
			});
		return compras.buscar(usuarioId, abertoPorLancamento.keySet()).stream().map(c -> {
			var r = abertoPorLancamento.get(c.id());
			return new CompraEmAberto(c.cartaoId(), c.fatura(), r.getPessoaId(), aberto.get(r.getId()));
		}).toList();
	}

}
