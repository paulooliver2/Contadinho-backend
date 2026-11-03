package br.com.contadinho.investimentos.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** O CDI de um dia útil, em % ao dia (série 12 do SGS). Gravado sem repetir dia pelo atualizador. */
@Entity
public class CdiDiario {

	@Id
	private LocalDate data;

	private BigDecimal taxa;

	protected CdiDiario() {
	}

	public LocalDate getData() {
		return data;
	}

	public BigDecimal getTaxa() {
		return taxa;
	}

}
