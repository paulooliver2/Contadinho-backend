package br.com.contadinho.receber.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Um valor que a pessoa devolveu ao dono (HU-19). Não fica ligado a um recebível: o abatimento é calculado, do que
 * vence primeiro para o último (RN-30), então trocar o responsável de uma compra não desfaz o pagamento (RN-16).
 */
@Entity
public class Pagamento {

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID pessoaId;

	private BigDecimal valor;

	private LocalDate recebidoEm;

	/** HU-19.3: desfeito não conta no saldo, mas fica guardado. */
	private Instant desfeitoEm;

	private Instant criadoEm;

	protected Pagamento() {
	}

	public Pagamento(UUID usuarioId, UUID pessoaId, BigDecimal valor, LocalDate recebidoEm, Instant agora) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.pessoaId = pessoaId;
		this.valor = valor.setScale(2, RoundingMode.UNNECESSARY);
		this.recebidoEm = recebidoEm;
		this.criadoEm = agora;
	}

	public void desfazer(Instant agora) {
		if (desfeitoEm == null) {
			desfeitoEm = agora;
		}
	}

	public boolean isDesfeito() {
		return desfeitoEm != null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPessoaId() {
		return pessoaId;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public LocalDate getRecebidoEm() {
		return recebidoEm;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
