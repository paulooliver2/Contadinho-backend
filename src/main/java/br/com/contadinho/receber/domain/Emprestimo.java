package br.com.contadinho.receber.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Dinheiro emprestado fora do cartão (HU-17). Cada parcela é um recebível; excluir o empréstimo apaga as parcelas. */
@Entity
public class Emprestimo {

	/** HU-17.1. */
	public static final Set<Integer> PARCELAS = Set.of(1, 2, 3, 6);

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID pessoaId;

	/** "Para quê"; opcional. */
	private String descricao;

	private BigDecimal valor;

	private short parcelas;

	/** Null é "Sem data" (RN-32): nenhuma parcela tem vencimento. */
	private LocalDate primeiraDevolucao;

	private Instant criadoEm;

	protected Emprestimo() {
	}

	public Emprestimo(UUID usuarioId, UUID pessoaId, String descricao, BigDecimal valor, int parcelas,
			LocalDate primeiraDevolucao, Instant agora) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.pessoaId = pessoaId;
		this.descricao = descricao;
		this.valor = valor.setScale(2, RoundingMode.UNNECESSARY);
		this.parcelas = (short) parcelas;
		this.primeiraDevolucao = primeiraDevolucao;
		this.criadoEm = agora;
	}

	public record Parcela(short numero, BigDecimal valor, LocalDate vencimento) {
	}

	/** RN-31 e RN-40: uma por mês; os centavos que sobram da divisão vão para a última. */
	public List<Parcela> dividir() {
		var cada = valor.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
		var ultima = valor.subtract(cada.multiply(BigDecimal.valueOf(parcelas - 1L)));
		var lista = new ArrayList<Parcela>();
		for (short n = 1; n <= parcelas; n++) {
			var vencimento = primeiraDevolucao == null ? null : primeiraDevolucao.plusMonths(n - 1L);
			lista.add(new Parcela(n, n == parcelas ? ultima : cada, vencimento));
		}
		return lista;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public UUID getPessoaId() {
		return pessoaId;
	}

	public String getDescricao() {
		return descricao;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public short getParcelas() {
		return parcelas;
	}

	public LocalDate getPrimeiraDevolucao() {
		return primeiraDevolucao;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
