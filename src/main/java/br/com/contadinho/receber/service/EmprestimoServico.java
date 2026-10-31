package br.com.contadinho.receber.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.pessoas.Pessoas;
import br.com.contadinho.receber.domain.Emprestimo;
import br.com.contadinho.receber.domain.Recebivel;
import br.com.contadinho.receber.repository.EmprestimoRepositorio;
import br.com.contadinho.receber.repository.RecebivelRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Valor emprestado fora do cartão (HU-17): cada parcela vira um recebível da pessoa (RN-31). */
@Service
public class EmprestimoServico {

	public static final String PESSOA_FORA_DA_LISTA = "Essa pessoa não está na sua lista. Escolha outra.";

	public static final String PARCELAS_INVALIDAS = "Escolha 1, 2, 3 ou 6 parcelas.";

	public static final String VALOR_PEQUENO = "Valor pequeno demais para tantas parcelas.";

	public static final String EMPRESTIMO_NAO_ENCONTRADO = "Empréstimo não encontrado.";

	private final EmprestimoRepositorio emprestimos;

	private final RecebivelRepositorio recebiveis;

	private final Pessoas pessoas;

	private final Clock clock;

	public EmprestimoServico(EmprestimoRepositorio emprestimos, RecebivelRepositorio recebiveis, Pessoas pessoas,
			Clock clock) {
		this.emprestimos = emprestimos;
		this.recebiveis = recebiveis;
		this.pessoas = pessoas;
		this.clock = clock;
	}

	/** {@code primeiraDevolucao} null é "Sem data" (RN-32). */
	public record Dados(UUID pessoaId, String descricao, BigDecimal valor, int parcelas, LocalDate primeiraDevolucao) {
	}

	/** Só para pessoa ativa (RN-36). Cada parcela precisa de ao menos um centavo. */
	@Transactional
	public Emprestimo registrar(UUID usuarioId, Dados dados) {
		if (!pessoas.podeEscolher(usuarioId, dados.pessoaId())) {
			throw ErroApi.campos(Map.of("pessoaId", PESSOA_FORA_DA_LISTA));
		}
		if (!Emprestimo.PARCELAS.contains(dados.parcelas())) {
			throw ErroApi.campos(Map.of("parcelas", PARCELAS_INVALIDAS));
		}
		if (dados.valor().compareTo(new BigDecimal("0.01").multiply(BigDecimal.valueOf(dados.parcelas()))) < 0) {
			throw ErroApi.campos(Map.of("valor", VALOR_PEQUENO));
		}
		var descricao = dados.descricao() == null || dados.descricao().isBlank() ? null : dados.descricao();
		var emprestimo = emprestimos.save(new Emprestimo(usuarioId, dados.pessoaId(), descricao, dados.valor(),
				dados.parcelas(), dados.primeiraDevolucao(), Instant.now(clock)));
		// Grava já o empréstimo, que as parcelas referenciam.
		emprestimos.flush();
		emprestimo.dividir().forEach(p -> recebiveis.save(Recebivel.daParcela(emprestimo, p)));
		return emprestimo;
	}

	/**
	 * Para corrigir um registro errado: o banco apaga as parcelas junto. Pagamentos já feitos continuam com a pessoa e
	 * abatem o que ela ainda deve (RN-16).
	 */
	@Transactional
	public void excluir(UUID usuarioId, UUID id) {
		var emprestimo = emprestimos.findByIdAndUsuarioId(id, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, EMPRESTIMO_NAO_ENCONTRADO));
		emprestimos.delete(emprestimo);
	}

}
