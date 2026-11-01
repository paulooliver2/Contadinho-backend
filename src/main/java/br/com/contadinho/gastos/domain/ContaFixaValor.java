package br.com.contadinho.gastos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** O valor de uma conta fixa a partir de um mês (HU-11.3): meses anteriores continuam com o valor que tinham. */
@Entity
public class ContaFixaValor {

	@Id
	private UUID id;

	private UUID contaFixaId;

	/** Primeiro dia do mês em que o valor passa a valer. */
	private LocalDate vigenteDesde;

	private BigDecimal valor;

	protected ContaFixaValor() {
	}

	public ContaFixaValor(UUID contaFixaId, YearMonth desde, BigDecimal valor) {
		this.id = UUID.randomUUID();
		this.contaFixaId = contaFixaId;
		this.vigenteDesde = desde.atDay(1);
		this.valor = valor.setScale(2, RoundingMode.UNNECESSARY);
	}

	/** Mudou de novo no mesmo mês: vale o último. */
	public void trocar(BigDecimal valor) {
		this.valor = valor.setScale(2, RoundingMode.UNNECESSARY);
	}

	public UUID getContaFixaId() {
		return contaFixaId;
	}

	public YearMonth getDesde() {
		return YearMonth.from(vigenteDesde);
	}

	public BigDecimal getValor() {
		return valor;
	}

}
